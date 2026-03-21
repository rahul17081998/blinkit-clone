package com.blinkit.inventory.service;

import com.blinkit.inventory.dto.request.ConfirmStockRequest;
import com.blinkit.inventory.dto.request.ReleaseStockRequest;
import com.blinkit.inventory.dto.request.ReserveStockRequest;
import com.blinkit.inventory.dto.request.UpdateStockRequest;
import com.blinkit.inventory.dto.response.StockResponse;
import com.blinkit.inventory.entity.Stock;
import com.blinkit.inventory.entity.StockMovement;
import com.blinkit.inventory.event.InventoryLowEvent;
import com.blinkit.inventory.event.InventoryOutEvent;
import com.blinkit.inventory.kafka.InventoryEventPublisher;
import com.blinkit.inventory.repository.StockMovementRepository;
import com.blinkit.inventory.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.blinkit.common.enums.StockMovementType;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryEventPublisher eventPublisher;

    public StockResponse getStock(String productId) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found for product"));
        return StockResponse.from(stock);
    }

    public StockResponse reserveStock(ReserveStockRequest req) {
        try {
            Stock stock = stockRepository.findByProductId(req.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found for product"));

            if (stock.getAvailableQty() < req.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock. Available: " + stock.getAvailableQty());
            }

            int prevQty = stock.getAvailableQty();
            stock.setAvailableQty(stock.getAvailableQty() - req.getQuantity());
            stock.setReservedQty(stock.getReservedQty() + req.getQuantity());
            stock.setTotalQty(stock.getAvailableQty() + stock.getReservedQty());

            Stock saved = stockRepository.save(stock);
            recordMovement(req.getProductId(), StockMovementType.RESERVE, -req.getQuantity(), prevQty, saved.getAvailableQty(), req.getOrderId(), null, "SYSTEM");
            checkAndPublishAlerts(saved);

            log.info("Reserved {} units for productId={}, orderId={}", req.getQuantity(), req.getProductId(), req.getOrderId());
            return StockResponse.from(saved);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock was modified concurrently, please retry");
        }
    }

    public StockResponse releaseStock(ReleaseStockRequest req) {
        try {
            Stock stock = stockRepository.findByProductId(req.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found for product"));

            int prevQty = stock.getAvailableQty();
            int releaseQty = Math.min(req.getQuantity(), stock.getReservedQty());
            stock.setReservedQty(stock.getReservedQty() - releaseQty);
            stock.setAvailableQty(stock.getAvailableQty() + releaseQty);
            stock.setTotalQty(stock.getAvailableQty() + stock.getReservedQty());

            Stock saved = stockRepository.save(stock);
            recordMovement(req.getProductId(), StockMovementType.RELEASE, releaseQty, prevQty, saved.getAvailableQty(), req.getOrderId(), null, "SYSTEM");

            log.info("Released {} units for productId={}, orderId={}", releaseQty, req.getProductId(), req.getOrderId());
            return StockResponse.from(saved);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock was modified concurrently, please retry");
        }
    }

    public StockResponse confirmStock(ConfirmStockRequest req) {
        try {
            Stock stock = stockRepository.findByProductId(req.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found for product"));

            int prevQty = stock.getAvailableQty();
            int confirmQty = Math.min(req.getQuantity(), stock.getReservedQty());
            stock.setReservedQty(stock.getReservedQty() - confirmQty);
            stock.setTotalQty(stock.getAvailableQty() + stock.getReservedQty());

            Stock saved = stockRepository.save(stock);
            recordMovement(req.getProductId(), StockMovementType.SALE, -confirmQty, prevQty, saved.getAvailableQty(), req.getOrderId(), null, "SYSTEM");

            log.info("Confirmed sale of {} units for productId={}, orderId={}", confirmQty, req.getProductId(), req.getOrderId());
            return StockResponse.from(saved);
        } catch (OptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock was modified concurrently, please retry");
        }
    }

    public StockResponse updateStock(String productId, UpdateStockRequest req, String adminUserId) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found for product"));

        int prevQty = stock.getAvailableQty();
        stock.setAvailableQty(stock.getAvailableQty() + req.getQuantityToAdd());
        stock.setTotalQty(stock.getAvailableQty() + stock.getReservedQty());
        stock.setLastRestockedAt(Instant.now());
        if (req.getLowStockThreshold() != null) {
            stock.setLowStockThreshold(req.getLowStockThreshold());
        }

        Stock saved = stockRepository.save(stock);
        recordMovement(productId, StockMovementType.RESTOCK, req.getQuantityToAdd(), prevQty, saved.getAvailableQty(), null, req.getReason(), adminUserId);

        log.info("Admin {} restocked productId={} by {}", adminUserId, productId, req.getQuantityToAdd());
        return StockResponse.from(saved);
    }

    public Page<StockResponse> getAllStock(Pageable pageable) {
        return stockRepository.findAll(pageable).map(StockResponse::from);
    }

    private void recordMovement(String productId, StockMovementType type, int qty, int prevQty, int newQty,
                                 String orderId, String reason, String performedBy) {
        StockMovement movement = StockMovement.builder()
                .productId(productId)
                .type(type)
                .quantity(qty)
                .previousAvailableQty(prevQty)
                .newAvailableQty(newQty)
                .orderId(orderId)
                .reason(reason)
                .performedBy(performedBy)
                .build();
        stockMovementRepository.save(movement);
    }

    private void checkAndPublishAlerts(Stock stock) {
        if (stock.getAvailableQty() == 0) {
            eventPublisher.publishInventoryOut(InventoryOutEvent.builder()
                    .productId(stock.getProductId())
                    .productName(stock.getProductName())
                    .build());
        } else if (stock.getAvailableQty() <= stock.getLowStockThreshold()) {
            eventPublisher.publishInventoryLow(InventoryLowEvent.builder()
                    .productId(stock.getProductId())
                    .productName(stock.getProductName())
                    .availableQty(stock.getAvailableQty())
                    .lowStockThreshold(stock.getLowStockThreshold())
                    .build());
        }
    }
}
