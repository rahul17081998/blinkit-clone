package com.blinkit.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TopUpRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "1.0", message = "Amount must be at least ₹1")
    private Double amount;

    private String description;
}
