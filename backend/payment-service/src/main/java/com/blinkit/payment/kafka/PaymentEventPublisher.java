package com.blinkit.payment.kafka;

import com.blinkit.payment.event.PaymentFailedEvent;
import com.blinkit.payment.event.PaymentSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    public static final String TOPIC_PAYMENT_SUCCESS = "payment.success";
    public static final String TOPIC_PAYMENT_FAILED  = "payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentSuccess(PaymentSuccessEvent event) {
        kafkaTemplate.send(TOPIC_PAYMENT_SUCCESS, event.getOrderId(), event);
        log.info("Published payment.success for orderId={}", event.getOrderId());
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(TOPIC_PAYMENT_FAILED, event.getOrderId(), event);
        log.info("Published payment.failed for orderId={}", event.getOrderId());
    }
}
