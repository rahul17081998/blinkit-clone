package com.blinkit.order.consumer;

import com.blinkit.common.enums.OrderStatus;
import com.blinkit.order.entity.Order;
import com.blinkit.order.event.DeliveryStatusUpdatedEvent;
import com.blinkit.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(
            topics = "delivery.status.updated",
            groupId = "order-service",
            containerFactory = "deliveryStatusKafkaListenerContainerFactory"
    )
    public void onDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        log.info("Received delivery.status.updated for orderId={} deliveryStatus={}",
                event.getOrderId(), event.getDeliveryStatus());

        Optional<Order> optOrder = orderRepository.findByOrderId(event.getOrderId());
        if (optOrder.isEmpty()) {
            log.warn("Order not found for orderId={}", event.getOrderId());
            return;
        }

        Order order = optOrder.get();
        OrderStatus newStatus = mapDeliveryStatus(event.getDeliveryStatus());
        if (newStatus == null) {
            log.info("[E2E] orderId={} delivery={} → no order status mapping (skipping)",
                    event.getOrderId(), event.getDeliveryStatus());
            return;
        }

        OrderStatus prevStatus = order.getStatus();
        order.setStatus(newStatus);
        orderRepository.save(order);
        log.info("[E2E] orderId={} order status: {} → {} (delivery: {})",
                event.getOrderId(), prevStatus, newStatus, event.getDeliveryStatus());
    }

    private OrderStatus mapDeliveryStatus(String deliveryStatus) {
        return switch (deliveryStatus) {
            case "PICKED_UP"        -> OrderStatus.PACKED;
            case "OUT_FOR_DELIVERY" -> OrderStatus.OUT_FOR_DELIVERY;
            case "DELIVERED"        -> OrderStatus.DELIVERED;
            default                 -> null;  // ASSIGNED, FAILED, CANCELLED → no change
        };
    }
}
