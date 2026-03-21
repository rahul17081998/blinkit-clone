package com.blinkit.coupon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class ValidateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    private String code;

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "cartTotal is required")
    @PositiveOrZero(message = "cartTotal must be >= 0")
    private Double cartTotal;
}
