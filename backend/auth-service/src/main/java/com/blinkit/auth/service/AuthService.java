package com.blinkit.auth.service;

import com.blinkit.auth.dto.request.LoginRequest;
import com.blinkit.auth.dto.request.ResetPasswordRequest;
import com.blinkit.auth.dto.request.SignupRequest;
import com.blinkit.auth.dto.response.AuthResponse;
import com.blinkit.auth.entity.AuthUser;
import com.blinkit.auth.event.UserDeletedEvent;
import com.blinkit.auth.event.UserPasswordResetEvent;
import com.blinkit.auth.event.UserRegisteredEvent;
import com.blinkit.auth.kafka.AuthEventPublisher;
import com.blinkit.auth.repository.AuthUserRepository;
import com.blinkit.auth.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.blinkit.common.enums.Role;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final PasswordResetService passwordResetService;
    private final AuthEventPublisher eventPublisher;

    // ── Signup ────────────────────────────────────────────────────

    public void signup(SignupRequest req) {
        if (userRepo.existsByEmail(req.getEmail().toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String userId = UUID.randomUUID().toString();
        AuthUser user = AuthUser.builder()
                .userId(userId)
                .email(req.getEmail().toLowerCase())
                .password(passwordEncoder.encode(req.getPassword()))
                .roles(List.of(Role.CUSTOMER))
                .isVerified(false)
                .isActive(true)
                .build();
        userRepo.save(user);

        String otp = otpService.generateAndStore(user.getEmail());

        eventPublisher.publishUserRegistered(UserRegisteredEvent.builder()
                .userId(userId)
                .email(user.getEmail())
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .otp(otp)
                .build());

        log.info("User registered: {}", user.getEmail());
    }

    // ── OTP Verify ────────────────────────────────────────────────

    public void verifyOtp(String email, String otp) {
        AuthUser user = findByEmail(email);
        if (user.getIsVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account already verified");
        }
        if (!otpService.validate(email.toLowerCase(), otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP");
        }
        user.setIsVerified(true);
        userRepo.save(user);
        log.info("User verified: {}", email);
    }

    // ── Login ─────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest req) {
        AuthUser user = findByEmail(req.getEmail());

        if (!user.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated");
        }
        if (!user.getIsVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your email first");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        List<Role> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User has no roles assigned");
        }
        String role = roles.get(0).name();
        String accessToken  = jwtUtil.generateAccessToken(user.getUserId(), user.getEmail(), role);
        String refreshToken = tokenService.createRefreshToken(user.getUserId());

        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getUserId())
                .email(user.getEmail())
                .role(role)
                .expiresIn(jwtUtil.getExpiryMinutes() * 60)
                .build();
    }

    // ── Refresh token ─────────────────────────────────────────────

    public AuthResponse refresh(String userId, String refreshToken) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refreshToken is required");
        }

        AuthUser user = userRepo.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!tokenService.validateRefreshToken(userId, refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        List<Role> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User has no roles assigned");
        }
        String role = roles.get(0).name();
        String newAccessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getEmail(), role);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .email(user.getEmail())
                .role(role)
                .expiresIn(jwtUtil.getExpiryMinutes() * 60)
                .build();
    }

    // ── Logout ────────────────────────────────────────────────────

    public void logout(String userId, String accessToken) {
        tokenService.deleteRefreshToken(userId);
        // blacklist the access token for its remaining TTL
        try {
            long expiry = jwtUtil.extractAllClaims(accessToken).getExpiration().getTime();
            long ttl = (expiry - System.currentTimeMillis()) / 1000;
            if (ttl > 0) tokenService.blacklistToken(accessToken, ttl);
        } catch (Exception e) {
            log.warn("Could not blacklist token: {}", e.getMessage());
        }
    }

    // ── Forgot password ───────────────────────────────────────────

    public void forgotPassword(String email) {
        AuthUser user = findByEmail(email);
        String token = passwordResetService.generateToken(user.getUserId());

        eventPublisher.publishUserPasswordReset(UserPasswordResetEvent.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .resetToken(token)
                .build());

        log.info("Password reset token generated for: {}", email);
    }

    // ── Validate reset token ──────────────────────────────────────

    public boolean validateResetToken(String token) {
        return passwordResetService.getUserId(token) != null;
    }

    // ── Reset password ────────────────────────────────────────────

    public void resetPassword(String token, ResetPasswordRequest req) {
        String userId = passwordResetService.getUserId(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Reset token expired or invalid");
        }

        AuthUser user = userRepo.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepo.save(user);
        passwordResetService.deleteToken(token);

        // Invalidate any existing refresh token
        tokenService.deleteRefreshToken(userId);
        log.info("Password reset successfully for userId={}", userId);
    }

    // ── Register Delivery Agent (admin-only, no OTP needed) ───────

    public Map<String, String> registerDeliveryAgent(String email, String password) {
        if (userRepo.existsByEmail(email.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        String userId = UUID.randomUUID().toString();
        AuthUser user = AuthUser.builder()
                .userId(userId)
                .email(email.toLowerCase())
                .password(passwordEncoder.encode(password))
                .roles(List.of(Role.DELIVERY_AGENT))
                .isVerified(true)
                .isActive(true)
                .build();
        userRepo.save(user);
        log.info("Delivery agent account created: {}", email);
        return Map.of("userId", userId, "email", email.toLowerCase());
    }

    // ── Delete account ────────────────────────────────────────────

    public void deleteAccount(String userId, String accessToken) {
        AuthUser user = userRepo.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Invalidate tokens first (same as logout)
        tokenService.deleteRefreshToken(userId);
        try {
            long expiry = jwtUtil.extractAllClaims(accessToken).getExpiration().getTime();
            long ttl = (expiry - System.currentTimeMillis()) / 1000;
            if (ttl > 0) tokenService.blacklistToken(accessToken, ttl);
        } catch (Exception e) {
            log.warn("Could not blacklist token during account deletion: {}", e.getMessage());
        }

        // Hard delete from auth_db
        userRepo.deleteByUserId(userId);
        log.info("Hard deleted auth account for userId={}", userId);

        // Notify other services to clean up
        eventPublisher.publishUserDeleted(UserDeletedEvent.builder()
                .userId(userId)
                .email(user.getEmail())
                .build());
    }

    // ── Admin lookup ──────────────────────────────────────────────

    public Map<String, Object> getAdminUserInfo(String userId) {
        AuthUser user = userRepo.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "roles", user.getRoles(),
                "isVerified", user.getIsVerified(),
                "isActive", user.getIsActive()
        );
    }

    // ── Helper ────────────────────────────────────────────────────

    private AuthUser findByEmail(String email) {
        return userRepo.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
