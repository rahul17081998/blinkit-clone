package com.blinkit.product.consumer;

import com.blinkit.product.event.InventoryOutEvent;
import com.blinkit.product.event.InventoryRestockEvent;
import com.blinkit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final ProductRepository productRepository;

    @KafkaListener(
        topics = "inventory.out",
        groupId = "product-service",
        containerFactory = "inventoryOutListenerFactory"
    )
    public void onInventoryOut(InventoryOutEvent event) {
        log.info("Received inventory.out event for productId={}", event.getProductId());
        productRepository.findByProductId(event.getProductId()).ifPresent(product -> {
            if (Boolean.TRUE.equals(product.getIsAvailable())) {
                product.setIsAvailable(false);
                productRepository.save(product);
                log.info("Marked product {} as unavailable due to out-of-stock", event.getProductId());
            }
        });
    }

    @KafkaListener(
        topics = "inventory.restock",
        groupId = "product-service",
        containerFactory = "inventoryRestockListenerFactory"
    )
    public void onInventoryRestock(InventoryRestockEvent event) {
        log.info("Received inventory.restock event for productId={}", event.getProductId());
        productRepository.findByProductId(event.getProductId()).ifPresent(product -> {
            if (!Boolean.TRUE.equals(product.getIsAvailable())) {
                product.setIsAvailable(true);
                productRepository.save(product);
                log.info("Marked product {} as available after restock", event.getProductId());
            }
        });
    }
}
