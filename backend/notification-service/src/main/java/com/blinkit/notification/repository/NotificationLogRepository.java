package com.blinkit.notification.repository;

import com.blinkit.notification.entity.NotificationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {
}
