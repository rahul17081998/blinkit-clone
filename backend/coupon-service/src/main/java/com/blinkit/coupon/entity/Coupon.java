package com.blinkit.coupon.entity;

import com.blinkit.common.enums.CouponType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "coupons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;           // always stored UPPERCASE

    private CouponType type;       // FLAT, PERCENT, FIRST_ORDER, FREE_DELIVERY

    private Double value;          // ₹ amount (FLAT/FIRST_ORDER) or % value (PERCENT)
    private Double maxDiscount;    // cap for PERCENT type (null = no cap)
    private Double minOrderAmount; // cart total must be >= this to apply

    private Integer usageLimit;    // global usage cap (null = unlimited)
    private Integer usedCount;     // incremented when order is confirmed
    private Integer perUserLimit;  // max times one user can use this coupon

    private Instant validFrom;
    private Instant validUntil;

    private Boolean isActive;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
