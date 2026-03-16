package com.blinkit.user.controller;

import com.blinkit.user.dto.request.AddressRequest;
import com.blinkit.user.dto.request.UpdateProfileRequest;
import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.user.entity.Address;
import com.blinkit.user.entity.UserProfile;
import com.blinkit.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        return ResponseEntity.status(ApiResponseCode.PROFILE_FETCHED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.PROFILE_FETCHED.getMessage(), userService.getProfile(userId)));
    }

    @Operation(summary = "Update my profile")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfile>> updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.status(ApiResponseCode.PROFILE_UPDATED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.PROFILE_UPDATED.getMessage(), userService.updateProfile(userId, email, req)));
    }

    // ── Addresses ─────────────────────────────────────────────────

    @Operation(summary = "List my addresses")
    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<List<Address>>> getAddresses(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.status(ApiResponseCode.ADDRESSES_FETCHED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.ADDRESSES_FETCHED.getMessage(), userService.getAddresses(userId)));
    }

    @Operation(summary = "Add a new address")
    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<Address>> addAddress(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.status(ApiResponseCode.ADDRESS_ADDED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.ADDRESS_ADDED.getMessage(), userService.addAddress(userId, req)));
    }

    @Operation(summary = "Update an address")
    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Address>> updateAddress(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String addressId,
            @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.status(ApiResponseCode.ADDRESS_UPDATED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.ADDRESS_UPDATED.getMessage(), userService.updateAddress(userId, addressId, req)));
    }

    @Operation(summary = "Delete an address")
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String addressId) {
        userService.deleteAddress(userId, addressId);
        return ResponseEntity.status(ApiResponseCode.ADDRESS_DELETED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.ADDRESS_DELETED.getMessage()));
    }

    @Operation(summary = "Set an address as default")
    @PutMapping("/addresses/{addressId}/default")
    public ResponseEntity<ApiResponse<Address>> setDefault(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String addressId) {
        return ResponseEntity.status(ApiResponseCode.DEFAULT_ADDRESS_SET.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.DEFAULT_ADDRESS_SET.getMessage(), userService.setDefaultAddress(userId, addressId)));
    }
}
