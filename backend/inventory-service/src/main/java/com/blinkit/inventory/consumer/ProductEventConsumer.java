package com.blinkit.inventory.consumer;

import com.blinkit.inventory.entity.Stock;
import com.blinkit.inventory.event.ProductCreatedEvent;
import com.blinkit.inventory.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final StockRepository stockRepository;

    @Value("${inventory.low-stock-default-threshold:10}")
    private int defaultLowStockThreshold;

    @KafkaListener(
        topics = "product.created",
        groupId = "inventory-service",
        containerFactory = "productCreatedListenerFactory"
    )
    public void onProductCreated(ProductCreatedEvent event) {
        log.info("Received product.created event for productId={}", event.getProductId());
        if (stockRepository.existsByProductId(event.getProductId())) {
            log.info("Stock already exists for productId={}, skipping", event.getProductId());
            return;
        }
        Stock stock = Stock.builder()
                .productId(event.getProductId())
                .productName(event.getProductName())
                .availableQty(0)
                .reservedQty(0)
                .totalQty(0)
                .lowStockThreshold(defaultLowStockThreshold)
                .unit(event.getUnit())
                .build();
        stockRepository.save(stock);
        log.info("Created stock entry for productId={}", event.getProductId());
    }
}
