package com.blinkit.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.password-reset-base-url:http://localhost:3000/reset-password}")
    private String passwordResetBaseUrl;

    public void sendOtpEmail(String toEmail, String firstName, String otp) {
        String subject = "Verify your Blinkit account";
        String body = String.format(
            "Hi %s,\n\n" +
            "Welcome to Blinkit! Please verify your email using the OTP below:\n\n" +
            "OTP: %s\n\n" +
            "This OTP is valid for 5 minutes.\n\n" +
            "If you did not create an account, please ignore this email.\n\n" +
            "Team Blinkit",
            firstName, otp
        );
        send(toEmail, subject, body);
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String resetLink = passwordResetBaseUrl + "/" + resetToken;
        String subject = "Reset your Blinkit password";
        String body = String.format(
            "Hi,\n\n" +
            "We received a request to reset your password.\n\n" +
            "Click the link below to reset your password:\n\n" +
            "%s\n\n" +
            "This link is valid for 15 minutes.\n\n" +
            "If you did not request a password reset, please ignore this email.\n\n" +
            "Team Blinkit",
            resetLink
        );
        send(toEmail, subject, body);
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromEmail);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
        log.info("Email sent to {}: {}", to, subject);
    }
}
