package com.blinkit.cart.client.dto;

import lombok.Data;

@Data
public class ProductDto {
    private String productId;
    private String name;
    private String thumbnailUrl;
    private String unit;
    private Double mrp;
    private Double sellingPrice;
    private Boolean isAvailable;
}
