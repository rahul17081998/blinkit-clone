package com.blinkit.coupon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicableCouponsResponse {

    /** Up to 3 coupons applicable right now (cartTotal >= minOrderAmount). */
    private List<CouponResponse> applicable;

    /** ID of the highest-saving coupon among applicable — pre-select this on the frontend. */
    private String bestCouponId;

    /** Present only when applicable is empty — the closest coupon to being unlocked. */
    private CouponNudge nudge;
}
