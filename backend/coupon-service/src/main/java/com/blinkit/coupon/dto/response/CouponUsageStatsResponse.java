package com.blinkit.coupon.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CouponUsageStatsResponse {

    private String couponId;
    private String couponCode;
    private long totalUsed;
    private Integer usageLimit;
    private Integer remaining;   // null if usageLimit is null (unlimited)
}
