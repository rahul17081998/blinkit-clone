package com.blinkit.payment.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.payment.dto.response.PaymentMethodResponse;
import com.blinkit.payment.dto.response.PaymentMethodsWithWalletResponse;
import com.blinkit.payment.service.PaymentMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    /**
     * Customer: get enabled payment methods + wallet balance (if authenticated).
     * X-User-Id is optional here — gateway injects it for authenticated requests.
     * For unauthenticated access the header is absent and walletBalance will be null.
     */
    @GetMapping("/methods")
    public ResponseEntity<ApiResponse<PaymentMethodsWithWalletResponse>> getEnabledMethods(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Payment methods fetched",
                paymentMethodService.getEnabledMethodsWithWallet(userId)));
    }

    /**
     * Admin: get ALL payment methods (enabled + disabled).
     */
    @GetMapping("/admin/methods")
    public ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> getAllMethods(
            @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new ApiResponse<>(false, "Access denied — ADMIN role required", null));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "All payment methods fetched",
                paymentMethodService.getAllMethods()));
    }

    /**
     * Admin: enable or disable a payment method.
     */
    @PutMapping("/admin/methods/{methodId}/toggle")
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> toggleMethod(
            @PathVariable String methodId,
            @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403)
                    .body(new ApiResponse<>(false, "Access denied — ADMIN role required", null));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "Payment method updated",
                paymentMethodService.toggleMethod(methodId)));
    }
}
