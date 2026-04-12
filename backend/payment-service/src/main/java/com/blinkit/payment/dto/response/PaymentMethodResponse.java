package com.blinkit.payment.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodResponse {
    private String methodId;
    private String displayName;
    private String description;
    private String iconType;
    private boolean enabled;
    private int displayOrder;
}
