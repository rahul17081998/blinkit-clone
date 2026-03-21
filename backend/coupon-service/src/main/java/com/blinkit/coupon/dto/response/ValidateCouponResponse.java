package com.blinkit.coupon.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidateCouponResponse {

    private boolean valid;
    private double discountAmount;
    private boolean isFreeDelivery;
    private String message;

    public static ValidateCouponResponse valid(double discountAmount, boolean isFreeDelivery, String message) {
        return ValidateCouponResponse.builder()
                .valid(true)
                .discountAmount(discountAmount)
                .isFreeDelivery(isFreeDelivery)
                .message(message)
                .build();
    }

    public static ValidateCouponResponse invalid(String reason) {
        return ValidateCouponResponse.builder()
                .valid(false)
                .discountAmount(0.0)
                .isFreeDelivery(false)
                .message(reason)
                .build();
    }
}
