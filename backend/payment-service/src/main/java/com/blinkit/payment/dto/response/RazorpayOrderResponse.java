package com.blinkit.payment.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayOrderResponse {
    private String razorpayOrderId;
    private String keyId;
    private long amountInPaise;
    private String currency;
    private String orderId;           // our internal orderId
}
