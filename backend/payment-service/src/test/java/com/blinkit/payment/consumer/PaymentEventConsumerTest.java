package com.blinkit.payment.consumer;

import com.blinkit.payment.event.OrderCancelledEvent;
import com.blinkit.payment.event.UserRegisteredEvent;
import com.blinkit.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock PaymentService paymentService;

    @InjectMocks PaymentEventConsumer consumer;

    @Test
    @DisplayName("onUserRegistered: delegates wallet creation to PaymentService")
    void onUserRegistered_delegatesToPaymentService() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId("user-1")
                .email("test@example.com")
                .build();

        consumer.onUserRegistered(event);

        verify(paymentService).createWallet("user-1");
    }

    @Test
    @DisplayName("onUserRegistered: swallows exception — does not propagate to Kafka")
    void onUserRegistered_exceptionIsSwallowed() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId("user-bad")
                .email("bad@example.com")
                .build();
        doThrow(new RuntimeException("DB error")).when(paymentService).createWallet("user-bad");

        // Should not throw
        consumer.onUserRegistered(event);

        verify(paymentService).createWallet("user-bad");
    }

    @Test
    @DisplayName("onOrderCancelled: delegates refund to PaymentService")
    void onOrderCancelled_delegatesToPaymentService() {
        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .amount(500.0)
                .cancelledAt(Instant.now())
                .build();

        consumer.onOrderCancelled(event);

        verify(paymentService).refund("order-1");
    }

    @Test
    @DisplayName("onOrderCancelled: swallows ResponseStatusException — does not crash consumer")
    void onOrderCancelled_exceptionIsSwallowed() {
        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .orderId("order-x")
                .userId("user-1")
                .amount(100.0)
                .cancelledAt(Instant.now())
                .build();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment found"))
                .when(paymentService).refund("order-x");

        // Should not throw
        consumer.onOrderCancelled(event);

        verify(paymentService).refund("order-x");
    }
}
