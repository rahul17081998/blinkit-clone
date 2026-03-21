package com.blinkit.notification.consumer;

import com.blinkit.common.enums.NotificationStatus;
import com.blinkit.common.enums.NotificationType;
import com.blinkit.notification.entity.NotificationLog;
import com.blinkit.notification.event.InventoryLowEvent;
import com.blinkit.notification.event.UserPasswordResetEvent;
import com.blinkit.notification.event.UserRegisteredEvent;
import com.blinkit.notification.repository.NotificationLogRepository;
import com.blinkit.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventConsumer {

    private final EmailService emailService;
    private final NotificationLogRepository logRepo;

    @Value("${ADMIN_EMAIL:rahul2140kumar@gmail.com}")
    private String adminEmail;

    @KafkaListener(topics = "user.registered", groupId = "notification-service",
                   containerFactory = "userRegisteredListenerFactory")
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received user.registered for userId={}", event.getUserId());
        NotificationStatus status = NotificationStatus.SENT;
        String error = null;
        try {
            emailService.sendOtpEmail(event.getEmail(), event.getFirstName(), event.getOtp());
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", event.getEmail(), e.getMessage());
            status = NotificationStatus.FAILED;
            error  = e.getMessage();
        }
        logRepo.save(NotificationLog.builder()
                .userId(event.getUserId())
                .email(event.getEmail())
                .type(NotificationType.USER_REGISTERED)
                .status(status)
                .errorMessage(error)
                .sentAt(Instant.now())
                .build());
    }

    @KafkaListener(topics = "user.password.reset", groupId = "notification-service",
                   containerFactory = "userPasswordResetListenerFactory")
    public void onUserPasswordReset(UserPasswordResetEvent event) {
        log.info("Received user.password.reset for userId={}", event.getUserId());
        NotificationStatus status = NotificationStatus.SENT;
        String error = null;
        try {
            emailService.sendPasswordResetEmail(event.getEmail(), event.getResetToken());
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", event.getEmail(), e.getMessage());
            status = NotificationStatus.FAILED;
            error  = e.getMessage();
        }
        logRepo.save(NotificationLog.builder()
                .userId(event.getUserId())
                .email(event.getEmail())
                .type(NotificationType.PASSWORD_RESET)
                .status(status)
                .errorMessage(error)
                .sentAt(Instant.now())
                .build());
    }

    @KafkaListener(topics = "inventory.low", groupId = "notification-service",
                   containerFactory = "inventoryLowListenerFactory")
    public void onInventoryLow(InventoryLowEvent event) {
        log.info("Received inventory.low event for productId={}, qty={}", event.getProductId(), event.getAvailableQty());
        NotificationStatus status = NotificationStatus.SENT;
        String error = null;
        try {
            String subject = "Low Stock Alert: " + event.getProductName();
            String body = String.format(
                "Hi Admin,\n\n" +
                "Stock for the following product is running low:\n\n" +
                "Product: %s (ID: %s)\n" +
                "Available Qty: %d\n" +
                "Low Stock Threshold: %d\n\n" +
                "Please restock at your earliest convenience.\n\n" +
                "Team Blinkit",
                event.getProductName(), event.getProductId(),
                event.getAvailableQty(), event.getLowStockThreshold()
            );
            emailService.sendAdminAlert(adminEmail, subject, body);
        } catch (Exception e) {
            log.error("Failed to send low-stock alert email: {}", e.getMessage());
            status = NotificationStatus.FAILED;
            error  = e.getMessage();
        }
        logRepo.save(NotificationLog.builder()
                .userId("SYSTEM")
                .email(adminEmail)
                .type(NotificationType.INVENTORY_LOW)
                .status(status)
                .errorMessage(error)
                .sentAt(Instant.now())
                .build());
    }
}
