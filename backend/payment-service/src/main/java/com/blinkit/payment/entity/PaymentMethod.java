package com.blinkit.payment.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payment_methods")
public class PaymentMethod {

    @Id
    private String id;

    @Indexed(unique = true)
    private String methodId;          // e.g. "WALLET", "RAZORPAY"

    private String displayName;       // e.g. "Wallet", "Razorpay"
    private String description;       // shown to customer
    private String iconType;          // "WALLET", "CARD", "UPI" (used by frontend for icon)
    private boolean enabled;
    private int displayOrder;         // lower = shown first

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
