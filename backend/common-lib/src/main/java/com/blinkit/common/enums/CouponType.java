package com.blinkit.common.enums;

public enum CouponType {
    FLAT,          // Fixed ₹ discount (e.g. ₹50 off)
    PERCENT,       // Percentage discount (e.g. 10% off, capped at maxDiscount)
    FIRST_ORDER,   // Only for first-time orderers (order-service check deferred to Stage 5)
    FREE_DELIVERY  // Sets deliveryFee = 0 regardless of cart total
}
