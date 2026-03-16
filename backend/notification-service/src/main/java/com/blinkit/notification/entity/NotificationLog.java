package com.blinkit.notification.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.blinkit.common.enums.NotificationStatus;
import com.blinkit.common.enums.NotificationType;

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
    private NotificationType type;
    private NotificationStatus status;
    private String errorMessage;
    private Instant sentAt;
}
