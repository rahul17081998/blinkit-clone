package com.blinkit.order.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CartItemDto {
    private String productId;
    private String name;
    private String imageUrl;
    private String unit;
    private double mrp;
    private double unitPrice;
    private int quantity;
    private double totalPrice;
    @JsonProperty("isAvailable")
    private boolean isAvailable;
}
