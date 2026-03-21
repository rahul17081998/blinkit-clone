package com.blinkit.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private String productId;
    private String name;
    private String imageUrl;
    private String unit;
    private double mrp;
    private double unitPrice;
    private int quantity;
    private double totalPrice;
}
