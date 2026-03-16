package com.blinkit.product.controller;

import com.blinkit.product.dto.request.CreateProductRequest;
import com.blinkit.product.dto.request.UpdateProductRequest;
import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.product.dto.response.ProductResponse;
import com.blinkit.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/products/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(
                    ApiResponseCode.ACCESS_DENIED.getHttpStatus(),
                    ApiResponseCode.ACCESS_DENIED.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CreateProductRequest req) {
        requireAdmin(role);
        return ResponseEntity.status(ApiResponseCode.PRODUCT_CREATED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.PRODUCT_CREATED.getMessage(), productService.createProduct(req, userId)));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String productId,
            @Valid @RequestBody UpdateProductRequest req) {
        requireAdmin(role);
        return ResponseEntity.status(ApiResponseCode.PRODUCT_UPDATED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.PRODUCT_UPDATED.getMessage(), productService.updateProduct(productId, req)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String productId) {
        requireAdmin(role);
        productService.deleteProduct(productId);
        return ResponseEntity.status(ApiResponseCode.PRODUCT_DELETED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.PRODUCT_DELETED.getMessage()));
    }

    @PutMapping("/{productId}/toggle")
    public ResponseEntity<ApiResponse<ProductResponse>> toggleAvailability(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String productId) {
        requireAdmin(role);
        return ResponseEntity.status(ApiResponseCode.PRODUCT_TOGGLED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.PRODUCT_TOGGLED.getMessage(), productService.toggleAvailability(productId)));
    }
}
