package com.blinkit.cart.client.dto;

import lombok.Data;

/**
 * Matches the ApiResponse<ProductDto> wrapper returned by product-service.
 */
@Data
public class ProductApiResponse {
    private boolean success;
    private String message;
    private ProductDto data;
}
