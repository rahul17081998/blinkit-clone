package com.blinkit.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RazorpayVerifyRequest {
    @NotBlank(message = "razorpayOrderId is required")
    private String razorpayOrderId;

    @NotBlank(message = "razorpayPaymentId is required")
    private String razorpayPaymentId;

    @NotBlank(message = "razorpaySignature is required")
    private String razorpaySignature;

    @NotBlank(message = "orderId is required")
    private String orderId;           // our internal orderId

    @NotBlank(message = "userId is required")
    private String userId;

    private String addressId;

    private Double amount;
}
