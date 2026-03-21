package com.blinkit.coupon.dto.request;

import com.blinkit.common.enums.CouponType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    private String code;

    @NotNull(message = "Coupon type is required")
    private CouponType type;

    private Double value;          // required for FLAT, PERCENT, FIRST_ORDER

    private Double maxDiscount;    // optional cap for PERCENT type

    @NotNull(message = "Minimum order amount is required")
    @Min(value = 0, message = "Minimum order amount cannot be negative")
    private Double minOrderAmount;

    private Integer usageLimit;    // null = unlimited

    @NotNull(message = "Per-user limit is required")
    @Min(value = 1, message = "Per-user limit must be at least 1")
    private Integer perUserLimit;

    @NotNull(message = "Valid from date is required")
    private Instant validFrom;

    @NotNull(message = "Valid until date is required")
    private Instant validUntil;

    @NotNull(message = "isActive is required")
    private Boolean isActive;
}
