package com.blinkit.notification.event;

import lombok.Data;

@Data
public class UserRegisteredEvent {
    private String userId;
    private String email;
    private String firstName;
    private String otp;
}
