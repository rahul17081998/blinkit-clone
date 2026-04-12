package com.blinkit.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RazorpayOrderRequest {
    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotNull(message = "amount is required")
    private Double amount;            // in rupees — will be converted to paise internally
}
