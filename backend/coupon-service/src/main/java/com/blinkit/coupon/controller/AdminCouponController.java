package com.blinkit.coupon.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.coupon.dto.request.CreateCouponRequest;
import com.blinkit.coupon.dto.request.UpdateCouponRequest;
import com.blinkit.coupon.dto.response.CouponResponse;
import com.blinkit.coupon.dto.response.CouponUsageStatsResponse;
import com.blinkit.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/coupons/admin")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(
                    ApiResponseCode.ACCESS_DENIED.getHttpStatus(),
                    ApiResponseCode.ACCESS_DENIED.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CreateCouponRequest req) {
        requireAdmin(role);
        CouponResponse created = couponService.createCoupon(req);
        return ResponseEntity.status(ApiResponseCode.COUPON_CREATED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.COUPON_CREATED.getMessage(), created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CouponResponse>>> getAllCoupons(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        requireAdmin(role);
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.COUPONS_FETCHED.getMessage(),
                        couponService.getAllCoupons(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> getCoupon(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id) {
        requireAdmin(role);
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.COUPON_FETCHED.getMessage(),
                        couponService.getCouponById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id,
            @Valid @RequestBody UpdateCouponRequest req) {
        requireAdmin(role);
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.COUPON_UPDATED.getMessage(),
                        couponService.updateCoupon(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id) {
        requireAdmin(role);
        couponService.deleteCoupon(id);
        return ResponseEntity.ok(ApiResponse.ok(ApiResponseCode.COUPON_DELETED.getMessage()));
    }

    @GetMapping("/{id}/usage")
    public ResponseEntity<ApiResponse<CouponUsageStatsResponse>> getUsageStats(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id) {
        requireAdmin(role);
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.COUPONS_FETCHED.getMessage(),
                        couponService.getUsageStats(id)));
    }
}
