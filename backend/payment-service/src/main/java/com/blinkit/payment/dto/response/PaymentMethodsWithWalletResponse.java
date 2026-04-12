package com.blinkit.payment.dto.response;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodsWithWalletResponse {
    private List<PaymentMethodResponse> methods;
    private Double walletBalance;    // null if not authenticated
}
