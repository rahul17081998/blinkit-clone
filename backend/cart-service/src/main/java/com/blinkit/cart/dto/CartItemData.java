package com.blinkit.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serialized as JSON and stored in Redis hash field value.
 * Key: cart:{userId}, field: productId, value: JSON of this class.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemData {
    private String productId;
    private String name;
    private String imageUrl;
    private String unit;
    private Double mrp;
    private Double unitPrice;  // sellingPrice at time of add
    private Integer quantity;
}
