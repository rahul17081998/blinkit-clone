package com.blinkit.auth.controller;

import com.blinkit.auth.dto.request.ForgotPasswordRequest;
import com.blinkit.auth.dto.request.ResetPasswordRequest;
import com.blinkit.auth.dto.response.ApiResponse;
import com.blinkit.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Password", description = "Forgot password and reset via email link")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordController {

    private final AuthService authService;

    @Operation(summary = "Send password reset link to email")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Password reset link sent to your email"));
    }

    @Operation(summary = "Validate reset token (called by frontend before showing reset form)")
    @GetMapping("/reset-password/validate/{token}")
    public ResponseEntity<ApiResponse<Void>> validateToken(@PathVariable String token) {
        if (authService.validateResetToken(token)) {
            return ResponseEntity.ok(ApiResponse.ok("Token is valid"));
        }
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.fail("Reset token expired or invalid"));
    }

    @Operation(summary = "Reset password using token from email link")
    @PostMapping("/reset-password/{token}")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable String token,
                                                            @Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(token, req);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully. Please login with your new password."));
    }
}
