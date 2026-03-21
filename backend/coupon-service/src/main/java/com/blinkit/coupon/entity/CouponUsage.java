package com.blinkit.coupon.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "coupon_usage")
@CompoundIndex(name = "coupon_user_idx", def = "{'couponId': 1, 'userId': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponUsage {

    @Id
    private String id;

    private String couponId;
    private String couponCode;
    private String userId;
    private String orderId;   // null at validate time; set when order confirmed (Stage 5)

    @CreatedDate
    private Instant usedAt;
}
