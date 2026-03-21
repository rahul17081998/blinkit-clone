package com.blinkit.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "userId_createdAt", def = "{'userId': 1, 'createdAt': -1}")
})
public class Transaction {

    @Id
    private String id;

    @Indexed(unique = true)
    private String transactionId;

    private String walletId;
    private String userId;

    @Indexed
    private String orderId;   // null for top-ups / signup bonus

    private String type;      // DEBIT | CREDIT
    private String reason;    // ORDER_PAYMENT | ORDER_REFUND | ADMIN_TOPUP | SIGNUP_BONUS
    private double amount;
    private double balanceBefore;
    private double balanceAfter;

    @Builder.Default
    private String status = "SUCCESS";

    private String description;

    @CreatedDate
    private Instant createdAt;
}
