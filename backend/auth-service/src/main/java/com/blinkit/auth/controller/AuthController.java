package com.blinkit.auth.controller;

import com.blinkit.auth.dto.request.LoginRequest;
import com.blinkit.auth.dto.request.SignupRequest;
import com.blinkit.common.dto.ApiResponse;
import com.blinkit.auth.dto.response.AuthResponse;
import com.blinkit.auth.service.AuthService;
import com.blinkit.common.enums.ApiResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Auth", description = "Signup, Login, OTP Verify, Refresh, Logout")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new customer")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest req) {
        authService.signup(req);
        return ResponseEntity.status(ApiResponseCode.REGISTRATION_SUCCESS.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.REGISTRATION_SUCCESS.getMessage()));
    }

    @Operation(summary = "Verify email with OTP")
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestParam String email,
                                                     @RequestParam String otp) {
        authService.verifyOtp(email, otp);
        return ResponseEntity.status(ApiResponseCode.EMAIL_VERIFIED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.EMAIL_VERIFIED.getMessage()));
    }

    @Operation(summary = "Login and receive JWT tokens")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse response = authService.login(req);
        return ResponseEntity.status(ApiResponseCode.LOGIN_SUCCESS.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.LOGIN_SUCCESS.getMessage(), response));
    }

    @Operation(summary = "Refresh access token using refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String refreshToken = body.get("refreshToken");
        AuthResponse response = authService.refresh(userId, refreshToken);
        return ResponseEntity.status(ApiResponseCode.TOKEN_REFRESHED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.TOKEN_REFRESHED.getMessage(), response));
    }

    @Operation(summary = "Register a delivery agent account (admin only)")
    @PostMapping("/delivery-agent")
    public ResponseEntity<ApiResponse<Map<String, String>>> registerDeliveryAgent(
            @RequestHeader("X-User-Role") String role,
            @RequestBody Map<String, String> body) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        String email = body.get("email");
        String password = body.get("password");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password are required");
        }
        Map<String, String> result = authService.registerDeliveryAgent(email, password);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Delivery agent account created", result));
    }

    @Operation(summary = "Get auth user info by userId (admin only)")
    @GetMapping("/admin/user/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminUserInfo(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String userId) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return ResponseEntity.ok(ApiResponse.ok("User found", authService.getAdminUserInfo(userId)));
    }

    @Operation(summary = "Delete my account permanently (hard delete)")
    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : "";
        authService.deleteAccount(userId, token);
        return ResponseEntity.status(ApiResponseCode.ACCOUNT_DELETED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.ACCOUNT_DELETED.getMessage()));
    }

    @Operation(summary = "Logout — invalidates tokens")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : "";
        authService.logout(userId, token);
        return ResponseEntity.status(ApiResponseCode.LOGOUT_SUCCESS.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.LOGOUT_SUCCESS.getMessage()));
    }
}
