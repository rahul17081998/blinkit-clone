package com.blinkit.cart.controller;

import com.blinkit.cart.dto.request.AddItemRequest;
import com.blinkit.cart.dto.request.ApplyPromoRequest;
import com.blinkit.cart.dto.request.UpdateItemRequest;
import com.blinkit.cart.dto.response.CartCountResponse;
import com.blinkit.cart.dto.response.CartResponse;
import com.blinkit.cart.service.CartService;
import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.CART_FETCHED.getMessage(),
                        cartService.getCart(userId)));
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<CartCountResponse>> getCount(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.CART_COUNT_FETCHED.getMessage(),
                        cartService.getCount(userId)));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody AddItemRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.ITEM_ADDED.getMessage(),
                        cartService.addItem(userId, req)));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String productId,
            @Valid @RequestBody UpdateItemRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.ITEM_UPDATED.getMessage(),
                        cartService.updateItem(userId, productId, req)));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String productId) {
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.ITEM_REMOVED.getMessage(),
                        cartService.removeItem(userId, productId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @RequestHeader("X-User-Id") String userId) {
        cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.ok(ApiResponseCode.CART_CLEARED.getMessage()));
    }

    @PostMapping("/promo")
    public ResponseEntity<ApiResponse<CartResponse>> applyPromo(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ApplyPromoRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.PROMO_APPLIED.getMessage(),
                        cartService.applyPromo(userId, req)));
    }

    @DeleteMapping("/promo")
    public ResponseEntity<ApiResponse<CartResponse>> removePromo(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.ok(ApiResponseCode.PROMO_REMOVED.getMessage(),
                        cartService.removePromo(userId)));
    }
}
