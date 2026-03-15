package com.blinkit.notification.event;

import lombok.Data;

@Data
public class UserPasswordResetEvent {
    private String userId;
    private String email;
    private String resetToken;
}
