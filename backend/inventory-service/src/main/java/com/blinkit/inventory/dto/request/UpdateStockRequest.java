package com.blinkit.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStockRequest {

    @NotNull(message = "Quantity to add is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantityToAdd;

    private String reason;

    private Integer lowStockThreshold;
}
