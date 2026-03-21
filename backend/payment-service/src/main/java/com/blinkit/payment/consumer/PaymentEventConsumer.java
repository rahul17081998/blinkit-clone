package com.blinkit.payment.consumer;

import com.blinkit.payment.event.OrderCancelledEvent;
import com.blinkit.payment.event.UserRegisteredEvent;
import com.blinkit.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = "user.registered",
            groupId = "payment-service",
            containerFactory = "userRegisteredKafkaListenerContainerFactory"
    )
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received user.registered for userId={}", event.getUserId());
        try {
            paymentService.createWallet(event.getUserId());
        } catch (Exception e) {
            log.error("Failed to create wallet for userId={}: {}", event.getUserId(), e.getMessage(), e);
        }
    }

    @KafkaListener(
            topics = "order.cancelled",
            groupId = "payment-service",
            containerFactory = "orderCancelledKafkaListenerContainerFactory"
    )
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received order.cancelled for orderId={}", event.getOrderId());
        try {
            paymentService.refund(event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to refund for orderId={}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }
}
