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

    // ── Coupon ────────────────────────────────────────────────────────
    COUPON_CREATED(HttpStatus.CREATED, "Coupon created"),
    COUPONS_FETCHED(HttpStatus.OK, "Coupons fetched"),
    COUPON_FETCHED(HttpStatus.OK, "Coupon fetched"),
    COUPON_UPDATED(HttpStatus.OK, "Coupon updated"),
    COUPON_DELETED(HttpStatus.OK, "Coupon deleted"),
    ACTIVE_COUPONS_FETCHED(HttpStatus.OK, "Active coupons fetched"),
    COUPON_USAGE_RECORDED(HttpStatus.OK, "Coupon usage recorded"),

    // ── Cart ──────────────────────────────────────────────────────────
    CART_FETCHED(HttpStatus.OK, "Cart fetched"),
    CART_CLEARED(HttpStatus.OK, "Cart cleared"),
    ITEM_ADDED(HttpStatus.OK, "Item added to cart"),
    ITEM_UPDATED(HttpStatus.OK, "Cart item updated"),
    ITEM_REMOVED(HttpStatus.OK, "Item removed from cart"),
    PROMO_APPLIED(HttpStatus.OK, "Promo code applied"),
    PROMO_REMOVED(HttpStatus.OK, "Promo code removed"),
    CART_COUNT_FETCHED(HttpStatus.OK, "Cart count fetched"),

    // ── Order ─────────────────────────────────────────────────────────
    ORDER_PLACED(HttpStatus.CREATED, "Order placed successfully"),
    ORDERS_FETCHED(HttpStatus.OK, "Orders fetched"),
    ORDER_FETCHED(HttpStatus.OK, "Order fetched"),
    ORDER_CANCELLED(HttpStatus.OK, "Order cancelled successfully"),
    ORDER_STATUS_UPDATED(HttpStatus.OK, "Order status updated"),

    // ── Payment / Wallet ──────────────────────────────────────────────
    WALLET_FETCHED(HttpStatus.OK, "Wallet details fetched"),
    WALLETS_FETCHED(HttpStatus.OK, "Wallets fetched"),
    TRANSACTION_HISTORY_FETCHED(HttpStatus.OK, "Transaction history fetched"),
    TRANSACTION_FETCHED(HttpStatus.OK, "Transaction fetched"),
    TRANSACTIONS_FETCHED(HttpStatus.OK, "Transactions fetched"),
    PAYMENT_SUCCESS(HttpStatus.OK, "Payment successful"),
    REFUND_SUCCESS(HttpStatus.OK, "Refund processed successfully"),
    WALLET_TOPUP_SUCCESS(HttpStatus.OK, "Wallet topped up successfully"),

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
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "Coupon not found"),
    COUPON_CODE_EXISTS(HttpStatus.CONFLICT, "Coupon with this code already exists"),
    COUPON_INVALID(HttpStatus.BAD_REQUEST, "Coupon is invalid or not applicable"),
    PRODUCT_UNAVAILABLE(HttpStatus.CONFLICT, "Product is not available"),
    MAX_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST, "Maximum quantity per item is 10"),
    RESET_TOKEN_EXPIRED(HttpStatus.GONE, "Reset token expired or invalid"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Wallet not found"),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found"),
    ORDER_CANNOT_CANCEL(HttpStatus.BAD_REQUEST, "Order cannot be cancelled in its current status"),
    INSUFFICIENT_WALLET_BALANCE(HttpStatus.BAD_REQUEST, "Insufficient wallet balance"),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "Cart is empty"),

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
