package com.blinkit.auth.controller;

import com.blinkit.auth.dto.request.LoginRequest;
import com.blinkit.auth.dto.request.SignupRequest;
import com.blinkit.auth.dto.response.ApiResponse;
import com.blinkit.auth.dto.response.AuthResponse;
import com.blinkit.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful. Check your email for OTP."));
    }

    @Operation(summary = "Verify email with OTP")
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestParam String email,
                                                     @RequestParam String otp) {
        authService.verifyOtp(email, otp);
        return ResponseEntity.ok(ApiResponse.ok("Email verified successfully. You can now login."));
    }

    @Operation(summary = "Login and receive JWT tokens")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse response = authService.login(req);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @Operation(summary = "Refresh access token using refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String refreshToken = body.get("refreshToken");
        AuthResponse response = authService.refresh(userId, refreshToken);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", response));
    }

    @Operation(summary = "Logout — invalidates tokens")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : "";
        authService.logout(userId, token);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }
}
