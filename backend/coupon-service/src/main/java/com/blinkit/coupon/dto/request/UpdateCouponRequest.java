package com.blinkit.coupon.dto.request;

import com.blinkit.common.enums.CouponType;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdateCouponRequest {

    private CouponType type;
    private Double value;
    private Double maxDiscount;
    private Double minOrderAmount;
    private Integer usageLimit;
    private Integer perUserLimit;
    private Instant validFrom;
    private Instant validUntil;
    private Boolean isActive;
}
