package com.blinkit.order.consumer;

import com.blinkit.common.enums.OrderStatus;
import com.blinkit.order.client.CouponServiceClient;
import com.blinkit.order.client.InventoryServiceClient;
import com.blinkit.order.client.dto.RecordUsageRequest;
import com.blinkit.order.client.dto.ReserveStockRequest;
import com.blinkit.order.entity.Order;
import com.blinkit.order.entity.OrderItem;
import com.blinkit.order.event.OrderConfirmedEvent;
import com.blinkit.order.event.PaymentFailedEvent;
import com.blinkit.order.event.PaymentSuccessEvent;
import com.blinkit.order.kafka.OrderEventPublisher;
import com.blinkit.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final InventoryServiceClient inventoryClient;
    private final CouponServiceClient couponClient;

    @KafkaListener(
            topics = "payment.success",
            groupId = "order-service",
            containerFactory = "paymentSuccessKafkaListenerContainerFactory"
    )
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        log.info("Received payment.success for orderId={}", event.getOrderId());

        Optional<Order> optOrder = orderRepository.findByOrderId(event.getOrderId());
        if (optOrder.isEmpty()) {
            log.warn("Order not found for orderId={}", event.getOrderId());
            return;
        }

        Order order = optOrder.get();
        if (order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            log.warn("Order {} is in status {} — skipping payment.success processing",
                    event.getOrderId(), order.getStatus());
            return;
        }

        // Update order to CONFIRMED
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentId(event.getPaymentId());
        orderRepository.save(order);
        log.info("Order {} status set to CONFIRMED", event.getOrderId());

        // Confirm stock in inventory for each item
        for (OrderItem item : order.getItems()) {
            try {
                inventoryClient.confirmStock(ReserveStockRequest.builder()
                        .productId(item.getProductId())
                        .orderId(order.getOrderId())
                        .quantity(item.getQuantity())
                        .build());
            } catch (Exception e) {
                log.error("Failed to confirm stock for product={} order={}: {}",
                        item.getProductId(), event.getOrderId(), e.getMessage());
            }
        }

        // Record coupon usage if coupon was applied
        if (order.getCouponCode() != null && !order.getCouponCode().isBlank()) {
            try {
                couponClient.recordUsage(RecordUsageRequest.builder()
                        .couponCode(order.getCouponCode())
                        .userId(order.getUserId())
                        .orderId(order.getOrderId())
                        .build());
            } catch (Exception e) {
                log.error("Failed to record coupon usage for order={}: {}", event.getOrderId(), e.getMessage());
            }
        }

        // Publish order.confirmed (cart-service clears cart, notification-service sends email)
        List<OrderConfirmedEvent.OrderItemInfo> itemInfos = order.getItems().stream()
                .map(i -> OrderConfirmedEvent.OrderItemInfo.builder()
                        .productId(i.getProductId())
                        .quantity(i.getQuantity())
                        .build())
                .collect(Collectors.toList());

        orderEventPublisher.publishOrderConfirmed(OrderConfirmedEvent.builder()
                .orderId(order.getOrderId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .couponCode(order.getCouponCode())
                .items(itemInfos)
                .totalAmount(order.getTotalAmount())
                .confirmedAt(Instant.now())
                .build());
    }

    @KafkaListener(
            topics = "payment.failed",
            groupId = "order-service",
            containerFactory = "paymentFailedKafkaListenerContainerFactory"
    )
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received payment.failed for orderId={}", event.getOrderId());

        Optional<Order> optOrder = orderRepository.findByOrderId(event.getOrderId());
        if (optOrder.isEmpty()) {
            log.warn("Order not found for orderId={}", event.getOrderId());
            return;
        }

        Order order = optOrder.get();
        if (order.getStatus() != OrderStatus.PAYMENT_PROCESSING &&
            order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            log.warn("Order {} is in status {} — skipping payment.failed processing",
                    event.getOrderId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);
        log.info("Order {} status set to PAYMENT_FAILED", event.getOrderId());

        // Release reserved stock
        for (OrderItem item : order.getItems()) {
            try {
                inventoryClient.releaseStock(ReserveStockRequest.builder()
                        .productId(item.getProductId())
                        .orderId(order.getOrderId())
                        .quantity(item.getQuantity())
                        .build());
            } catch (Exception e) {
                log.error("Failed to release stock for product={} order={}: {}",
                        item.getProductId(), event.getOrderId(), e.getMessage());
            }
        }
    }
}
