package com.blinkit.auth.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPasswordResetEvent {
    private String userId;
    private String email;
    private String resetToken;  // UUID token for password reset link
}
