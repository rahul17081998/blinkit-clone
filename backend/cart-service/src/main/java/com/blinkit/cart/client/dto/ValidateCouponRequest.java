package com.blinkit.cart.client.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidateCouponRequest {
    private String code;
    private String userId;
    private Double cartTotal;
}
