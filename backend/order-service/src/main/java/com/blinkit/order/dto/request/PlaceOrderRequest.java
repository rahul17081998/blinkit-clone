package com.blinkit.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlaceOrderRequest {

    @NotBlank(message = "addressId is required")
    private String addressId;

    private String notes;
}
