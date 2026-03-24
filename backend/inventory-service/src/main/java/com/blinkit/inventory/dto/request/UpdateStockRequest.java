package com.blinkit.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStockRequest {

    @NotNull(message = "Quantity is required")
    private Integer quantityToAdd;

    private String reason;

    private Integer lowStockThreshold;
}
