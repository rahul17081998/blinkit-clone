package com.blinkit.notification.consumer;

import com.blinkit.notification.entity.NotificationLog;
import com.blinkit.notification.event.UserPasswordResetEvent;
import com.blinkit.notification.event.UserRegisteredEvent;
import com.blinkit.notification.repository.NotificationLogRepository;
import com.blinkit.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventConsumer {

    private final EmailService emailService;
    private final NotificationLogRepository logRepo;

    @KafkaListener(topics = "user.registered", groupId = "notification-service",
                   containerFactory = "userRegisteredListenerFactory")
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received user.registered for userId={}", event.getUserId());
        String status = "SENT";
        String error  = null;
        try {
            emailService.sendOtpEmail(event.getEmail(), event.getFirstName(), event.getOtp());
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", event.getEmail(), e.getMessage());
            status = "FAILED";
            error  = e.getMessage();
        }
        logRepo.save(NotificationLog.builder()
                .userId(event.getUserId())
                .email(event.getEmail())
                .type("USER_REGISTERED")
                .status(status)
                .errorMessage(error)
                .sentAt(Instant.now())
                .build());
    }

    @KafkaListener(topics = "user.password.reset", groupId = "notification-service",
                   containerFactory = "userPasswordResetListenerFactory")
    public void onUserPasswordReset(UserPasswordResetEvent event) {
        log.info("Received user.password.reset for userId={}", event.getUserId());
        String status = "SENT";
        String error  = null;
        try {
            emailService.sendPasswordResetEmail(event.getEmail(), event.getResetToken());
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", event.getEmail(), e.getMessage());
            status = "FAILED";
            error  = e.getMessage();
        }
        logRepo.save(NotificationLog.builder()
                .userId(event.getUserId())
                .email(event.getEmail())
                .type("PASSWORD_RESET")
                .status(status)
                .errorMessage(error)
                .sentAt(Instant.now())
                .build());
    }
}
