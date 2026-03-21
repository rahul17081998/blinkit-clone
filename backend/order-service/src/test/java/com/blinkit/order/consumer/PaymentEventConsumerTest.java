package com.blinkit.order.consumer;

import com.blinkit.common.enums.OrderStatus;
import com.blinkit.order.client.CouponServiceClient;
import com.blinkit.order.client.InventoryServiceClient;
import com.blinkit.order.client.dto.ReserveStockRequest;
import com.blinkit.order.entity.Order;
import com.blinkit.order.entity.OrderItem;
import com.blinkit.order.event.OrderConfirmedEvent;
import com.blinkit.order.event.PaymentFailedEvent;
import com.blinkit.order.event.PaymentSuccessEvent;
import com.blinkit.order.kafka.OrderEventPublisher;
import com.blinkit.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderEventPublisher orderEventPublisher;
    @Mock InventoryServiceClient inventoryClient;
    @Mock CouponServiceClient couponClient;

    @InjectMocks PaymentEventConsumer consumer;

    private Order processingOrder;
    private PaymentSuccessEvent successEvent;
    private PaymentFailedEvent failedEvent;

    @BeforeEach
    void setUp() {
        OrderItem item = OrderItem.builder()
                .productId("prod-1").name("Milk").quantity(2).totalPrice(90.0).build();

        processingOrder = Order.builder()
                .orderId("order-1")
                .orderNumber("BLK-20260322-0001")
                .userId("user-1")
                .items(List.of(item))
                .totalAmount(100.0)
                .status(OrderStatus.PAYMENT_PROCESSING)
                .build();

        successEvent = PaymentSuccessEvent.builder()
                .paymentId("tx-1")
                .orderId("order-1")
                .userId("user-1")
                .amount(100.0)
                .paidAt(Instant.now())
                .build();

        failedEvent = PaymentFailedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .amount(100.0)
                .reason("Insufficient balance")
                .failedAt(Instant.now())
                .build();
    }

    // ── onPaymentSuccess ────────────────────────────────────────────────

    @Test
    @DisplayName("onPaymentSuccess: sets order CONFIRMED, confirms stock, publishes order.confirmed")
    void onPaymentSuccess_processingOrder_confirmsOrderAndPublishesEvent() {
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(processingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.onPaymentSuccess(successEvent);

        assertThat(processingOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(processingOrder.getPaymentId()).isEqualTo("tx-1");

        verify(inventoryClient).confirmStock(any(ReserveStockRequest.class));

        ArgumentCaptor<OrderConfirmedEvent> eventCaptor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(orderEventPublisher).publishOrderConfirmed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("onPaymentSuccess: skips if order not found")
    void onPaymentSuccess_orderNotFound_skips() {
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        consumer.onPaymentSuccess(successEvent);

        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderConfirmed(any());
    }

    @Test
    @DisplayName("onPaymentSuccess: skips idempotently if order already CONFIRMED")
    void onPaymentSuccess_alreadyConfirmed_skips() {
        processingOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(processingOrder));

        consumer.onPaymentSuccess(successEvent);

        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderConfirmed(any());
    }

    @Test
    @DisplayName("onPaymentSuccess: records coupon usage when coupon code present")
    void onPaymentSuccess_withCoupon_recordsCouponUsage() {
        processingOrder.setCouponCode("SAVE10");
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(processingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.onPaymentSuccess(successEvent);

        verify(couponClient).recordUsage(any());
    }

    @Test
    @DisplayName("onPaymentSuccess: skips coupon recording when no coupon")
    void onPaymentSuccess_noCoupon_skipsCouponUsage() {
        processingOrder.setCouponCode(null);
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(processingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.onPaymentSuccess(successEvent);

        verify(couponClient, never()).recordUsage(any());
    }

    // ── onPaymentFailed ─────────────────────────────────────────────────

    @Test
    @DisplayName("onPaymentFailed: sets order PAYMENT_FAILED and releases stock")
    void onPaymentFailed_processingOrder_setsFailedAndReleasesStock() {
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(processingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.onPaymentFailed(failedEvent);

        assertThat(processingOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

        verify(inventoryClient).releaseStock(any(ReserveStockRequest.class));
    }

    @Test
    @DisplayName("onPaymentFailed: skips idempotently if already PAYMENT_FAILED")
    void onPaymentFailed_alreadyFailed_skips() {
        processingOrder.setStatus(OrderStatus.PAYMENT_FAILED);
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(processingOrder));

        consumer.onPaymentFailed(failedEvent);

        verify(orderRepository, never()).save(any());
        verify(inventoryClient, never()).releaseStock(any());
    }

    @Test
    @DisplayName("onPaymentFailed: skips if order not found")
    void onPaymentFailed_orderNotFound_skips() {
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        consumer.onPaymentFailed(failedEvent);

        verify(orderRepository, never()).save(any());
    }
}
