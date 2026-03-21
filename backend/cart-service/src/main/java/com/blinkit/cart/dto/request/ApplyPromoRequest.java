package com.blinkit.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyPromoRequest {

    @NotBlank(message = "Promo code is required")
    private String code;
}
