package com.blinkit.cart.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartItemResponse {
    private String productId;
    private String name;
    private String imageUrl;
    private String unit;
    private Double mrp;
    private Double unitPrice;
    private Integer quantity;
    private Double totalPrice;
    private Boolean isAvailable;
}
