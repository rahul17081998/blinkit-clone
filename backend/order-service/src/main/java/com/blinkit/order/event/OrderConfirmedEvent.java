package com.blinkit.order.event;

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
public class OrderConfirmedEvent {
    private String orderId;
    private String orderNumber;
    private String userId;
    private String couponCode;
    private List<OrderItemInfo> items;
    private double totalAmount;
    private Instant confirmedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemInfo {
        private String productId;
        private int quantity;
    }
}
