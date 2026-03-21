package com.blinkit.coupon.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.coupon.dto.request.RecordUsageRequest;
import com.blinkit.coupon.dto.request.ValidateCouponRequest;
import com.blinkit.coupon.dto.response.CouponResponse;
import com.blinkit.coupon.dto.response.ValidateCouponResponse;
import com.blinkit.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    // Public — list active coupons (for frontend promo banner)
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> getActiveCoupons() {
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.ACTIVE_COUPONS_FETCHED.getMessage(),
                        couponService.getActiveCoupons()));
    }

    // Internal — validate coupon (called by cart-service via Feign, blocked at gateway)
    @PostMapping("/validate")
    public ResponseEntity<ValidateCouponResponse> validate(
            @Valid @RequestBody ValidateCouponRequest req) {
        return ResponseEntity.ok(couponService.validate(req));
    }

    // Internal — record coupon usage (called by order-service in Stage 5)
    @PostMapping("/usage")
    public ResponseEntity<ApiResponse<Void>> recordUsage(
            @Valid @RequestBody RecordUsageRequest req) {
        couponService.recordUsage(req);
        return ResponseEntity.ok(ApiResponse.ok(ApiResponseCode.COUPON_USAGE_RECORDED.getMessage()));
    }
}
