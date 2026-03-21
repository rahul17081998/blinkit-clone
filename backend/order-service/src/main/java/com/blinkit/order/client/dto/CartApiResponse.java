package com.blinkit.order.client.dto;

import lombok.Data;

@Data
public class CartApiResponse {
    private boolean success;
    private String message;
    private CartDto data;
}
