package com.blinkit.product.kafka;

import com.blinkit.product.event.ProductCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishProductCreated(ProductCreatedEvent event) {
        kafkaTemplate.send(ProductCreatedEvent.TOPIC, event.getProductId(), event);
        log.info("Published product.created event for productId={}", event.getProductId());
    }
}
