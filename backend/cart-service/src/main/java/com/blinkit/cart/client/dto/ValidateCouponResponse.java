package com.blinkit.cart.client.dto;

import lombok.Data;

@Data
public class ValidateCouponResponse {
    private boolean valid;
    private double discountAmount;
    private boolean isFreeDelivery;
    private String message;
}
