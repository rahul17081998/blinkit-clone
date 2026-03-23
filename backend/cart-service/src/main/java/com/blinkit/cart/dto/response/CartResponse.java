package com.blinkit.cart.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CartResponse {
    private List<CartItemResponse> items;
    private double itemsTotal;
    private double deliveryFee;
    private String couponCode;         // discount coupon (FLAT / PERCENT)
    private double couponDiscount;
    private String deliveryCouponCode; // free-delivery coupon (FREE_DELIVERY)
    private boolean isFreeDelivery;
    private double totalAmount;
    private int totalItems;   // sum of all quantities
}
