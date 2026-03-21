package com.blinkit.order.kafka;

import com.blinkit.order.event.OrderCancelledEvent;
import com.blinkit.order.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    public static final String TOPIC_ORDER_CONFIRMED = "order.confirmed";
    public static final String TOPIC_ORDER_CANCELLED = "order.cancelled";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderConfirmed(OrderConfirmedEvent event) {
        kafkaTemplate.send(TOPIC_ORDER_CONFIRMED, event.getOrderId(), event);
        log.info("Published order.confirmed for orderId={}", event.getOrderId());
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        kafkaTemplate.send(TOPIC_ORDER_CANCELLED, event.getOrderId(), event);
        log.info("Published order.cancelled for orderId={}", event.getOrderId());
    }
}
