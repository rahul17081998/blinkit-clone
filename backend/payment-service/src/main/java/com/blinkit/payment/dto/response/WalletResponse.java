package com.blinkit.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private String walletId;
    private String userId;
    private double balance;
    private String currency;
    private boolean isActive;
}
