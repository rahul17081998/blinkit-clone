package com.blinkit.inventory.kafka;

import com.blinkit.inventory.event.InventoryLowEvent;
import com.blinkit.inventory.event.InventoryOutEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInventoryLow(InventoryLowEvent event) {
        kafkaTemplate.send(InventoryLowEvent.TOPIC, event.getProductId(), event);
        log.info("Published inventory.low event for productId={}, qty={}", event.getProductId(), event.getAvailableQty());
    }

    public void publishInventoryOut(InventoryOutEvent event) {
        kafkaTemplate.send(InventoryOutEvent.TOPIC, event.getProductId(), event);
        log.info("Published inventory.out event for productId={}", event.getProductId());
    }
}
