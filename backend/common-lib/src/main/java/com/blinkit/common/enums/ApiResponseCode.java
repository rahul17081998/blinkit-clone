package com.blinkit.common.enums;

import org.springframework.http.HttpStatus;

public enum ApiResponseCode {

    // ── 2xx ──────────────────────────────────────────────────────────
    SUCCESS(HttpStatus.OK, "Success"),
    CREATED(HttpStatus.CREATED, "Resource created successfully"),

    // ── Auth ─────────────────────────────────────────────────────────
    REGISTRATION_SUCCESS(HttpStatus.CREATED, "Registration successful. Check your email for OTP."),
    EMAIL_VERIFIED(HttpStatus.OK, "Email verified successfully. You can now login."),
    LOGIN_SUCCESS(HttpStatus.OK, "Login successful"),
    TOKEN_REFRESHED(HttpStatus.OK, "Token refreshed"),
    LOGOUT_SUCCESS(HttpStatus.OK, "Logged out successfully"),
    PASSWORD_RESET_LINK_SENT(HttpStatus.OK, "Password reset link sent to your email"),
    RESET_TOKEN_VALID(HttpStatus.OK, "Token is valid"),
    PASSWORD_RESET_SUCCESS(HttpStatus.OK, "Password reset successfully. Please login with your new password."),

    // ── User / Profile ────────────────────────────────────────────────
    PROFILE_FETCHED(HttpStatus.OK, "Profile fetched"),
    PROFILE_UPDATED(HttpStatus.OK, "Profile updated"),
    ADDRESSES_FETCHED(HttpStatus.OK, "Addresses fetched"),
    ADDRESS_ADDED(HttpStatus.CREATED, "Address added"),
    ADDRESS_UPDATED(HttpStatus.OK, "Address updated"),
    ADDRESS_DELETED(HttpStatus.OK, "Address deleted"),
    DEFAULT_ADDRESS_SET(HttpStatus.OK, "Default address updated"),

    // ── Product / Category ────────────────────────────────────────────
    PRODUCTS_FETCHED(HttpStatus.OK, "Products fetched"),
    PRODUCT_FETCHED(HttpStatus.OK, "Product fetched"),
    PRODUCT_CREATED(HttpStatus.CREATED, "Product created"),
    PRODUCT_UPDATED(HttpStatus.OK, "Product updated"),
    PRODUCT_DELETED(HttpStatus.OK, "Product deleted"),
    PRODUCT_TOGGLED(HttpStatus.OK, "Product status toggled"),
    CATEGORIES_FETCHED(HttpStatus.OK, "Categories fetched"),
    CATEGORY_CREATED(HttpStatus.CREATED, "Category created"),

    // ── Inventory ─────────────────────────────────────────────────────
    STOCK_FETCHED(HttpStatus.OK, "Stock fetched"),
    ALL_STOCK_FETCHED(HttpStatus.OK, "All stock fetched"),
    STOCK_UPDATED(HttpStatus.OK, "Stock updated"),
    STOCK_RESERVED(HttpStatus.OK, "Stock reserved"),
    STOCK_RELEASED(HttpStatus.OK, "Stock released"),
    STOCK_CONFIRMED(HttpStatus.OK, "Stock confirmed"),

    // ── 4xx Errors ────────────────────────────────────────────────────
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "Invalid or expired OTP"),
    ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "Account already verified"),
    MISSING_PARAM(HttpStatus.BAD_REQUEST, "Missing required parameter"),
    MISSING_HEADER(HttpStatus.BAD_REQUEST, "Missing required header"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired or blacklisted"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Please verify your email first"),
    ACCOUNT_DEACTIVATED(HttpStatus.FORBIDDEN, "Account is deactivated"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied: insufficient role"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Profile not found"),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "Address not found"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Category not found"),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "Stock not found for product"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already registered"),
    PRODUCT_SLUG_EXISTS(HttpStatus.CONFLICT, "Product with this slug already exists"),
    CATEGORY_NAME_EXISTS(HttpStatus.CONFLICT, "Category with this name already exists"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock"),
    RESET_TOKEN_EXPIRED(HttpStatus.GONE, "Reset token expired or invalid"),

    // ── 5xx Errors ────────────────────────────────────────────────────
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");

    private final HttpStatus httpStatus;
    private final String message;

    ApiResponseCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
