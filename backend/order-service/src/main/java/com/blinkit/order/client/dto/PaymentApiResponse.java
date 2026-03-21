package com.blinkit.order.client.dto;

import lombok.Data;

@Data
public class PaymentApiResponse {
    private boolean success;
    private String message;
    private PaymentResponseDto data;
}
