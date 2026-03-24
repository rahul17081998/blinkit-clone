package com.blinkit.coupon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponNudge {

    private CouponResponse coupon;
    private double amountNeeded;
    private String message;
}
