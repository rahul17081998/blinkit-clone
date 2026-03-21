package com.blinkit.cart.consumer;

import com.blinkit.cart.event.OrderConfirmedEvent;
import com.blinkit.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final CartService cartService;

    @KafkaListener(
            topics = "order.confirmed",
            groupId = "cart-service",
            containerFactory = "orderConfirmedListenerFactory"
    )
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Received order.confirmed for orderId={}, userId={}", event.getOrderId(), event.getUserId());
        cartService.clearCart(event.getUserId());
        log.info("Cart cleared for userId={} after order confirmation", event.getUserId());
    }
}
