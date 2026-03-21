package com.blinkit.order.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class CartDto {
    private List<CartItemDto> items;
    private double itemsTotal;
    private double deliveryFee;
    private String couponCode;
    private double couponDiscount;
    private boolean isFreeDelivery;
    private double totalAmount;
    private int totalItems;
}
