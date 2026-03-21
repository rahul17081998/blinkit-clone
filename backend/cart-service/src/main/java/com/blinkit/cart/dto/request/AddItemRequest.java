package com.blinkit.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddItemRequest {

    @NotBlank(message = "productId is required")
    private String productId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 10, message = "Maximum quantity per item is 10")
    private Integer quantity;
}
