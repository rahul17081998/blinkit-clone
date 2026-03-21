package com.blinkit.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayRequest {
    private String orderId;
    private String userId;
    private Double amount;
    private String description;
}
