package com.blinkit.order.service;

import com.blinkit.common.enums.OrderStatus;
import com.blinkit.order.client.CartServiceClient;
import com.blinkit.order.client.InventoryServiceClient;
import com.blinkit.order.client.PaymentServiceClient;
import com.blinkit.order.client.dto.CartDto;
import com.blinkit.order.client.dto.CartItemDto;
import com.blinkit.order.client.dto.PayRequest;
import com.blinkit.order.client.dto.PaymentApiResponse;
import com.blinkit.order.client.dto.ReserveStockRequest;
import com.blinkit.order.dto.request.PlaceOrderRequest;
import com.blinkit.order.dto.request.UpdateOrderStatusRequest;
import com.blinkit.order.dto.response.OrderItemResponse;
import com.blinkit.order.dto.response.OrderResponse;
import com.blinkit.order.entity.Order;
import com.blinkit.order.entity.OrderItem;
import com.blinkit.order.event.OrderCancelledEvent;
import com.blinkit.order.kafka.OrderEventPublisher;
import com.blinkit.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartServiceClient cartClient;
    private final PaymentServiceClient paymentClient;
    private final InventoryServiceClient inventoryClient;
    private final OrderEventPublisher orderEventPublisher;
    private final StringRedisTemplate redisTemplate;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── Place order ───────────────────────────────────────────────────

    public OrderResponse placeOrder(String userId, PlaceOrderRequest req) {
        // 1. Fetch cart
        CartDto cart;
        try {
            cart = cartClient.getCart(userId).getData();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cart service unavailable");
        }

        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        // 2. Validate all items are available
        boolean hasUnavailable = cart.getItems().stream()
                .anyMatch(item -> !item.isAvailable());
        if (hasUnavailable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Some items in your cart are unavailable. Please remove them and try again.");
        }

        String orderId = UUID.randomUUID().toString();
        String orderNumber = generateOrderNumber();

        // 3. Reserve stock for all items
        List<OrderItem> orderItems = cart.getItems().stream()
                .map(i -> OrderItem.builder()
                        .productId(i.getProductId())
                        .name(i.getName())
                        .imageUrl(i.getImageUrl())
                        .unit(i.getUnit())
                        .mrp(i.getMrp())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .totalPrice(i.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        List<String> reservedProducts = new java.util.ArrayList<>();
        try {
            for (CartItemDto item : cart.getItems()) {
                inventoryClient.reserveStock(ReserveStockRequest.builder()
                        .productId(item.getProductId())
                        .orderId(orderId)
                        .quantity(item.getQuantity())
                        .build());
                reservedProducts.add(item.getProductId());
            }
        } catch (feign.FeignException.Conflict e) {
            // Rollback already reserved items
            rollbackReservedStock(orderId, cart.getItems(), reservedProducts);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for one or more items");
        } catch (Exception e) {
            rollbackReservedStock(orderId, cart.getItems(), reservedProducts);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory service unavailable");
        }

        // 4. Save order with PAYMENT_PENDING status
        Order order = Order.builder()
                .orderId(orderId)
                .orderNumber(orderNumber)
                .userId(userId)
                .addressId(req.getAddressId())
                .items(orderItems)
                .couponCode(cart.getCouponCode())
                .couponDiscount(cart.getCouponDiscount())
                .itemsTotal(cart.getItemsTotal())
                .deliveryFee(cart.getDeliveryFee())
                .totalAmount(cart.getTotalAmount())
                .status(OrderStatus.PAYMENT_PENDING)
                .notes(req.getNotes())
                .build();
        orderRepository.save(order);
        log.info("Order {} created with status PAYMENT_PENDING", orderId);

        // 5. Call payment-service to debit wallet
        try {
            PaymentApiResponse payResp = paymentClient.pay(PayRequest.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .addressId(req.getAddressId())
                    .amount(cart.getTotalAmount())
                    .description("Payment for order " + orderNumber)
                    .build());

            // 6. Update status to PAYMENT_PROCESSING (will move to CONFIRMED via Kafka payment.success)
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            if (payResp.getData() != null) {
                order.setPaymentId(payResp.getData().getTransactionId());
            }
            orderRepository.save(order);
            log.info("Order {} payment initiated — status: PAYMENT_PROCESSING", orderId);

        } catch (feign.FeignException.BadRequest e) {
            // Insufficient balance — payment-service already published payment.failed
            // Update order status to PAYMENT_FAILED and release stock
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);

            for (CartItemDto item : cart.getItems()) {
                try {
                    inventoryClient.releaseStock(ReserveStockRequest.builder()
                            .productId(item.getProductId())
                            .orderId(orderId)
                            .quantity(item.getQuantity())
                            .build());
                } catch (Exception ex) {
                    log.error("Failed to release stock on payment failure for product={}", item.getProductId());
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment failed: Insufficient wallet balance");
        } catch (Exception e) {
            log.error("Payment service error for orderId={}: {}", orderId, e.getMessage(), e);
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);
            rollbackReservedStock(orderId, cart.getItems(), reservedProducts);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment service unavailable");
        }

        return toOrderResponse(order);
    }

    // ── Get order by ID (customer owns it) ───────────────────────────

    public OrderResponse getOrder(String userId, String orderId) {
        Order order = orderRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return toOrderResponse(order);
    }

    // ── Get all orders for a user ─────────────────────────────────────

    public Page<OrderResponse> getMyOrders(String userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toOrderResponse);
    }

    // ── Cancel order ──────────────────────────────────────────────────

    public OrderResponse cancelOrder(String userId, String orderId) {
        Order order = orderRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING &&
            order.getStatus() != OrderStatus.CONFIRMED &&
            order.getStatus() != OrderStatus.PACKED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order cannot be cancelled in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order {} cancelled by userId={}", orderId, userId);

        // Publish order.cancelled → payment-service refunds wallet + inventory releases stock
        orderEventPublisher.publishOrderCancelled(OrderCancelledEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(order.getTotalAmount())
                .cancelledAt(Instant.now())
                .build());

        return toOrderResponse(order);
    }

    // ── Admin: all orders ─────────────────────────────────────────────

    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toOrderResponse);
    }

    // ── Admin: get single order ───────────────────────────────────────

    public OrderResponse getOrderAdmin(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return toOrderResponse(order);
    }

    // ── Admin: update status ──────────────────────────────────────────

    public OrderResponse updateStatus(String orderId, UpdateOrderStatusRequest req) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        order.setStatus(req.getStatus());
        orderRepository.save(order);
        log.info("Admin updated order {} status to {}", orderId, req.getStatus());
        return toOrderResponse(order);
    }

    // ── Helpers ───────────────────────────────────────────────────────

   /* private String generateOrderNumber() {
        String date = LocalDate.now(IST).format(DATE_FMT);
        String counterKey = "order:counter:" + date;

        // Seed Redis from MongoDB if key is missing (e.g. after Redis restart)
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(counterKey))) {
            long existingCount = orderRepository.countByOrderNumberStartingWith("^BLK-" + date + "-");
            if (existingCount > 0) {
                redisTemplate.opsForValue().set(counterKey, String.valueOf(existingCount));
                log.info("Seeded order counter for {} from MongoDB: {}", date, existingCount);
            }
        }

        Long seq = redisTemplate.opsForValue().increment(counterKey);
        redisTemplate.expire(counterKey, Duration.ofDays(2));
        return String.format("BLK-%s-%04d", date, seq);
    }*/

    private String generateOrderNumber() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String time = now.format(DateTimeFormatter.ofPattern("HHmmssSSS")); // milliseconds

        String key = "order:counter:" + date + ":" + time;

        Long seq = redisTemplate.opsForValue().increment(key);

        // expire quickly since per-ms key
        redisTemplate.expire(key, Duration.ofMinutes(2));

        return String.format("BLK-%s-%s-%04d", date, time, seq);
    }


    private void rollbackReservedStock(String orderId, List<CartItemDto> items, List<String> reservedProductIds) {
        for (CartItemDto item : items) {
            if (reservedProductIds.contains(item.getProductId())) {
                try {
                    inventoryClient.releaseStock(ReserveStockRequest.builder()
                            .productId(item.getProductId())
                            .orderId(orderId)
                            .quantity(item.getQuantity())
                            .build());
                } catch (Exception ex) {
                    log.error("Rollback failed for product={}: {}", item.getProductId(), ex.getMessage());
                }
            }
        }
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> OrderItemResponse.builder()
                        .productId(i.getProductId())
                        .name(i.getName())
                        .imageUrl(i.getImageUrl())
                        .unit(i.getUnit())
                        .mrp(i.getMrp())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .totalPrice(i.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .addressId(order.getAddressId())
                .items(items)
                .couponCode(order.getCouponCode())
                .couponDiscount(order.getCouponDiscount())
                .itemsTotal(order.getItemsTotal())
                .deliveryFee(order.getDeliveryFee())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentId(order.getPaymentId())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
