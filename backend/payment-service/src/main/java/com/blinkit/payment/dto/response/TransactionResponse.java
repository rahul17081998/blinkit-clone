package com.blinkit.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private String transactionId;
    private String walletId;
    private String userId;
    private String orderId;
    private String type;
    private String reason;
    private double amount;
    private double balanceBefore;
    private double balanceAfter;
    private String status;
    private String description;
    private Instant createdAt;
}
