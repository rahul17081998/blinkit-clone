package com.blinkit.order.client.dto;

import lombok.Data;

@Data
public class PaymentResponseDto {
    private String transactionId;
    private double walletBalance;
    private double amount;
    private String status;
}
