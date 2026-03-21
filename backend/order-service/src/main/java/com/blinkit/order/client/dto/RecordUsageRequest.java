package com.blinkit.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordUsageRequest {
    private String couponCode;
    private String userId;
    private String orderId;
}
