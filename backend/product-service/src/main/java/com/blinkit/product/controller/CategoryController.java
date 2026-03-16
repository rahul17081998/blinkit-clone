package com.blinkit.product.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.common.enums.ApiResponseCode;
import com.blinkit.product.dto.response.CategoryResponse;
import com.blinkit.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(
            @RequestParam(defaultValue = "flat") String view) {
        List<CategoryResponse> categories = "tree".equals(view)
                ? categoryService.getCategoryTree()
                : categoryService.getAllCategories();
        return ResponseEntity.status(ApiResponseCode.CATEGORIES_FETCHED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.CATEGORIES_FETCHED.getMessage(), categories));
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable String categoryId) {
        return ResponseEntity.status(ApiResponseCode.CATEGORIES_FETCHED.getHttpStatus())
                .body(ApiResponse.ok(ApiResponseCode.CATEGORIES_FETCHED.getMessage(), categoryService.getCategoryById(categoryId)));
    }
}
