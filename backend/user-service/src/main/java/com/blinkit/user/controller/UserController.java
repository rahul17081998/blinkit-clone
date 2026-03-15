package com.blinkit.user.controller;

import com.blinkit.user.dto.request.AddressRequest;
import com.blinkit.user.dto.request.UpdateProfileRequest;
import com.blinkit.user.dto.response.ApiResponse;
import com.blinkit.user.entity.Address;
import com.blinkit.user.entity.UserProfile;
import com.blinkit.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Users", description = "Profile and Address management")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Profile ───────────────────────────────────────────────────

    @Operation(summary = "Get my profile")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfile>> getProfile(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok("Profile fetched", userService.getProfile(userId)));
    }

    @Operation(summary = "Update my profile")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfile>> updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", userService.updateProfile(userId, req)));
    }

    // ── Addresses ─────────────────────────────────────────────────

    @Operation(summary = "List my addresses")
    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<List<Address>>> getAddresses(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok("Addresses fetched", userService.getAddresses(userId)));
    }

    @Operation(summary = "Add a new address")
    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<Address>> addAddress(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Address added", userService.addAddress(userId, req)));
    }

    @Operation(summary = "Update an address")
    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Address>> updateAddress(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String addressId,
            @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Address updated", userService.updateAddress(userId, addressId, req)));
    }

    @Operation(summary = "Delete an address")
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String addressId) {
        userService.deleteAddress(userId, addressId);
        return ResponseEntity.ok(ApiResponse.ok("Address deleted"));
    }

    @Operation(summary = "Set an address as default")
    @PutMapping("/addresses/{addressId}/default")
    public ResponseEntity<ApiResponse<Address>> setDefault(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String addressId) {
        return ResponseEntity.ok(ApiResponse.ok("Default address updated", userService.setDefaultAddress(userId, addressId)));
    }
}
