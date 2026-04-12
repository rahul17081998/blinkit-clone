package com.blinkit.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlaceOrderRequest {

    @NotBlank(message = "addressId is required")
    private String addressId;

    private String notes;

    @Builder.Default
    private String paymentMethod = "WALLET";  // "WALLET" or "RAZORPAY"
}
