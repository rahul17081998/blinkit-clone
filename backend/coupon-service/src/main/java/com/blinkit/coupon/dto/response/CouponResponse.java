package com.blinkit.coupon.dto.response;

import com.blinkit.common.enums.CouponType;
import com.blinkit.coupon.entity.Coupon;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CouponResponse {

    private String id;
    private String code;
    private CouponType type;
    private Double value;
    private Double maxDiscount;
    private Double minOrderAmount;
    private Integer usageLimit;
    private Integer usedCount;
    private Integer perUserLimit;
    private Instant validFrom;
    private Instant validUntil;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public static CouponResponse from(Coupon c) {
        return CouponResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .type(c.getType())
                .value(c.getValue())
                .maxDiscount(c.getMaxDiscount())
                .minOrderAmount(c.getMinOrderAmount())
                .usageLimit(c.getUsageLimit())
                .usedCount(c.getUsedCount())
                .perUserLimit(c.getPerUserLimit())
                .validFrom(c.getValidFrom())
                .validUntil(c.getValidUntil())
                .isActive(c.getIsActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
