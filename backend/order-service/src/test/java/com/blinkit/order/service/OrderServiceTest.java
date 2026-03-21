package com.blinkit.order.service;

import com.blinkit.common.enums.OrderStatus;
import com.blinkit.order.client.CartServiceClient;
import com.blinkit.order.client.InventoryServiceClient;
import com.blinkit.order.client.PaymentServiceClient;
import com.blinkit.order.client.dto.*;
import com.blinkit.order.dto.request.PlaceOrderRequest;
import com.blinkit.order.dto.request.UpdateOrderStatusRequest;
import com.blinkit.order.dto.response.OrderResponse;
import com.blinkit.order.entity.Order;
import com.blinkit.order.entity.OrderItem;
import com.blinkit.order.event.OrderCancelledEvent;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CartServiceClient cartClient;
    @Mock PaymentServiceClient paymentClient;
    @Mock InventoryServiceClient inventoryClient;
    @Mock OrderEventPublisher orderEventPublisher;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks OrderService orderService;

    private CartItemDto cartItem;
    private CartDto cart;
    private CartApiResponse cartApiResponse;
    private Order existingOrder;

    @BeforeEach
    void setUp() {
        cartItem = new CartItemDto();
        cartItem.setProductId("prod-1");
        cartItem.setName("Milk");
        cartItem.setUnit("1L");
        cartItem.setMrp(50.0);
        cartItem.setUnitPrice(45.0);
        cartItem.setQuantity(2);
        cartItem.setTotalPrice(90.0);
        cartItem.setAvailable(true);

        cart = new CartDto();
        cart.setItems(List.of(cartItem));
        cart.setItemsTotal(90.0);
        cart.setDeliveryFee(10.0);
        cart.setTotalAmount(100.0);
        cart.setCouponCode(null);
        cart.setCouponDiscount(0.0);

        cartApiResponse = new CartApiResponse();
        cartApiResponse.setSuccess(true);
        cartApiResponse.setData(cart);

        existingOrder = Order.builder()
                .orderId("order-1")
                .orderNumber("BLK-20260322-0001")
                .userId("user-1")
                .addressId("addr-1")
                .items(List.of(OrderItem.builder()
                        .productId("prod-1").name("Milk").quantity(2).totalPrice(90.0).build()))
                .totalAmount(100.0)
                .status(OrderStatus.CONFIRMED)
                .createdAt(Instant.now())
                .build();
    }

    // ── placeOrder ─────────────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder: success — saves order PAYMENT_PROCESSING and returns response")
    void placeOrder_success_savesOrderAndReturnsResponse() {
        when(cartClient.getCart("user-1")).thenReturn(cartApiResponse);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        PaymentResponseDto paymentData = new PaymentResponseDto();
        paymentData.setTransactionId("tx-1");
        paymentData.setWalletBalance(9900.0);
        PaymentApiResponse payResp = new PaymentApiResponse();
        payResp.setSuccess(true);
        payResp.setData(paymentData);
        when(paymentClient.pay(any(PayRequest.class))).thenReturn(payResp);

        // Capture saves
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId("addr-1");

        OrderResponse response = orderService.placeOrder("user-1", req);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getTotalAmount()).isEqualTo(100.0);
        assertThat(response.getPaymentId()).isEqualTo("tx-1");

        verify(inventoryClient).reserveStock(any(ReserveStockRequest.class));
        verify(paymentClient).pay(any(PayRequest.class));
    }

    @Test
    @DisplayName("placeOrder: empty cart — throws 400")
    void placeOrder_emptyCart_throws400() {
        CartApiResponse emptyResp = new CartApiResponse();
        emptyResp.setSuccess(true);
        emptyResp.setData(new CartDto()); // no items

        when(cartClient.getCart("user-1")).thenReturn(emptyResp);

        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId("addr-1");

        assertThatThrownBy(() -> orderService.placeOrder("user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(inventoryClient, never()).reserveStock(any());
        verify(paymentClient, never()).pay(any());
    }

    @Test
    @DisplayName("placeOrder: unavailable item in cart — throws 409")
    void placeOrder_unavailableItem_throws409() {
        cartItem.setAvailable(false);
        when(cartClient.getCart("user-1")).thenReturn(cartApiResponse);

        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId("addr-1");

        assertThatThrownBy(() -> orderService.placeOrder("user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(inventoryClient, never()).reserveStock(any());
    }

    @Test
    @DisplayName("placeOrder: payment BadRequest — sets order PAYMENT_FAILED and throws 400")
    void placeOrder_paymentBadRequest_setsPaymentFailed() {
        when(cartClient.getCart("user-1")).thenReturn(cartApiResponse);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        doThrow(feign.FeignException.BadRequest.class)
                .when(paymentClient).pay(any(PayRequest.class));

        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAddressId("addr-1");

        assertThatThrownBy(() -> orderService.placeOrder("user-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Stock should be released on payment failure
        verify(inventoryClient).releaseStock(any(ReserveStockRequest.class));
    }

    // ── getOrder ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrder: returns order when userId matches")
    void getOrder_found_returnsOrderResponse() {
        when(orderRepository.findByOrderIdAndUserId("order-1", "user-1"))
                .thenReturn(Optional.of(existingOrder));

        OrderResponse response = orderService.getOrder("user-1", "order-1");

        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("getOrder: throws 404 when order not found for user")
    void getOrder_notFound_throws404() {
        when(orderRepository.findByOrderIdAndUserId("order-x", "user-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder("user-1", "order-x"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── cancelOrder ────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder: CONFIRMED order — sets CANCELLED and publishes event")
    void cancelOrder_confirmedOrder_cancelsAndPublishesEvent() {
        when(orderRepository.findByOrderIdAndUserId("order-1", "user-1"))
                .thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder("user-1", "order-1");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventPublisher).publishOrderCancelled(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("cancelOrder: DELIVERED order — throws 400 (cannot cancel)")
    void cancelOrder_deliveredOrder_throws400() {
        existingOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findByOrderIdAndUserId("order-1", "user-1"))
                .thenReturn(Optional.of(existingOrder));

        assertThatThrownBy(() -> orderService.cancelOrder("user-1", "order-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(orderEventPublisher, never()).publishOrderCancelled(any());
    }

    @Test
    @DisplayName("cancelOrder: OUT_FOR_DELIVERY order — throws 400 (cannot cancel)")
    void cancelOrder_outForDelivery_throws400() {
        existingOrder.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        when(orderRepository.findByOrderIdAndUserId("order-1", "user-1"))
                .thenReturn(Optional.of(existingOrder));

        assertThatThrownBy(() -> orderService.cancelOrder("user-1", "order-1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── updateStatus (admin) ───────────────────────────────────────────

    @Test
    @DisplayName("updateStatus: admin updates order status")
    void updateStatus_success_updatesStatus() {
        when(orderRepository.findByOrderId("order-1")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
        req.setStatus(OrderStatus.PACKED);

        OrderResponse response = orderService.updateStatus("order-1", req);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PACKED);
    }

    // ── getMyOrders ────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyOrders: returns paginated orders for user")
    void getMyOrders_returnsPaginatedOrders() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-1", pageable))
                .thenReturn(new PageImpl<>(List.of(existingOrder)));

        Page<OrderResponse> result = orderService.getMyOrders("user-1", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getOrderId()).isEqualTo("order-1");
    }
}
