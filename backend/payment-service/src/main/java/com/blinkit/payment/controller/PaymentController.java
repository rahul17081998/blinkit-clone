package com.blinkit.payment.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.payment.dto.request.PayRequest;
import com.blinkit.payment.dto.response.PaymentResponse;
import com.blinkit.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal-only endpoints for order-service to call via Feign.
 * Protected by InternalRequestFilter (X-Internal-Secret required).
 */
@RestController
@RequestMapping("/payments/internal")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/pay")
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            @Valid @RequestBody PayRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.PAYMENT_SUCCESS.getMessage(),
                paymentService.pay(req)));
    }

    @PostMapping("/refund/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponseCode.REFUND_SUCCESS.getMessage(),
                paymentService.refund(orderId)));
    }
}
