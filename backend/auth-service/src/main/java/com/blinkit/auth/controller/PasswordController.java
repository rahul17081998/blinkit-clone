package com.blinkit.auth.controller;

import com.blinkit.auth.dto.request.ForgotPasswordRequest;
import com.blinkit.auth.dto.request.ResetPasswordRequest;
import com.blinkit.common.dto.ApiResponse;
import com.blinkit.auth.service.AuthService;
import com.blinkit.common.enums.ApiResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        return ResponseEntity.status(ApiResponseCode.PASSWORD_RESET_LINK_SENT.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.PASSWORD_RESET_LINK_SENT.getMessage()));
    }

    @Operation(summary = "Validate reset token (called by frontend before showing reset form)")
    @GetMapping("/reset-password/validate/{token}")
    public ResponseEntity<ApiResponse<Void>> validateToken(@PathVariable String token) {
        if (authService.validateResetToken(token)) {
            return ResponseEntity.status(ApiResponseCode.RESET_TOKEN_VALID.getHttpStatus())
                    .body(ApiResponse.ok(ApiResponseCode.RESET_TOKEN_VALID.getMessage()));
        }
        return ResponseEntity.status(ApiResponseCode.RESET_TOKEN_EXPIRED.getHttpStatus())
                .body(ApiResponse.fail(ApiResponseCode.RESET_TOKEN_EXPIRED.getMessage()));
    }

    @Operation(summary = "Reset password using token from email link")
    @PostMapping("/reset-password/{token}")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable String token,
                                                            @Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(token, req);
        return ResponseEntity.status(ApiResponseCode.PASSWORD_RESET_SUCCESS.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.PASSWORD_RESET_SUCCESS.getMessage()));
    }
}
