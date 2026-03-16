package com.blinkit.product.controller;

import com.blinkit.product.dto.request.CreateProductRequest;
import com.blinkit.product.dto.request.UpdateProductRequest;
import com.blinkit.common.dto.ApiResponse;
import com.blinkit.product.dto.response.ProductResponse;
import com.blinkit.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CreateProductRequest req) {
        requireAdmin(role);
        ProductResponse product = productService.createProduct(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Product created", product));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String productId,
            @Valid @RequestBody UpdateProductRequest req) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok("Product updated", productService.updateProduct(productId, req)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String productId) {
        requireAdmin(role);
        productService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted"));
    }

    @PutMapping("/{productId}/toggle")
    public ResponseEntity<ApiResponse<ProductResponse>> toggleAvailability(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String productId) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok("Availability toggled", productService.toggleAvailability(productId)));
    }
}
