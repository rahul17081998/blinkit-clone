package com.blinkit.payment.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.payment.dto.request.RazorpayOrderRequest;
import com.blinkit.payment.dto.request.RazorpayVerifyRequest;
import com.blinkit.payment.dto.response.RazorpayOrderResponse;
import com.blinkit.payment.service.RazorpayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments/razorpay")
@RequiredArgsConstructor
public class RazorpayController {

    private final RazorpayService razorpayService;

    /**
     * Step 1: Create a Razorpay Order.
     * Called by frontend after our internal order is placed (PAYMENT_PENDING).
     */
    @PostMapping("/create-order")
    public ResponseEntity<ApiResponse<RazorpayOrderResponse>> createOrder(
            @Valid @RequestBody RazorpayOrderRequest req,
            @RequestHeader("X-User-Id") String userId) {
        RazorpayOrderResponse response = razorpayService.createOrder(req.getOrderId(), req.getAmount());
        return ResponseEntity.ok(ApiResponse.ok("Razorpay order created", response));
    }

    /**
     * Step 2: Verify payment after Razorpay popup completes.
     * Verifies HMAC signature → publishes payment.success Kafka event.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyPayment(
            @Valid @RequestBody RazorpayVerifyRequest req,
            @RequestHeader("X-User-Id") String userId) {
        req.setUserId(userId);  // override with gateway-injected userId for security
        razorpayService.verifyAndPublish(req);
        return ResponseEntity.ok(ApiResponse.ok("Payment verified successfully", null));
    }
}
