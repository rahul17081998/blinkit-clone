package com.blinkit.order.dto.response;

import com.blinkit.common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private String orderNumber;
    private String userId;
    private String addressId;
    private List<OrderItemResponse> items;
    private String couponCode;
    private double couponDiscount;
    private double itemsTotal;
    private double deliveryFee;
    private double totalAmount;
    private OrderStatus status;
    private String paymentId;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
