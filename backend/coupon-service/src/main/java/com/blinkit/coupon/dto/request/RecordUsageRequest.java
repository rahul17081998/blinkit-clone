package com.blinkit.coupon.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecordUsageRequest {

    @NotBlank(message = "couponCode is required")
    private String couponCode;

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "orderId is required")
    private String orderId;
}
