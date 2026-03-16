package com.blinkit.product.controller;

import com.blinkit.product.dto.request.CreateCategoryRequest;
import com.blinkit.product.dto.response.ApiResponse;
import com.blinkit.product.dto.response.CategoryResponse;
import com.blinkit.product.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/categories/admin")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CreateCategoryRequest req) {
        requireAdmin(role);
        CategoryResponse category = categoryService.createCategory(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Category created", category));
    }
}
