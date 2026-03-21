package com.blinkit.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateItemRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "quantity must be >= 0 (0 removes the item)")
    @Max(value = 10, message = "Maximum quantity per item is 10")
    private Integer quantity;
}
