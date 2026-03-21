package com.blinkit.user.event;

import lombok.Data;

@Data
public class UserRegisteredEvent {
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String otp;
}
