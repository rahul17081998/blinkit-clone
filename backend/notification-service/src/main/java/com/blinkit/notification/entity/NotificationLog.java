package com.blinkit.notification.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "notification_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    private String id;

    private String userId;
    private String email;
    private String type;        // USER_REGISTERED, PASSWORD_RESET
    private String status;      // SENT, FAILED
    private String errorMessage;
    private Instant sentAt;
}
