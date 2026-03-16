package com.blinkit.product.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.product.dto.response.ProductResponse;
import com.blinkit.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok("Products fetched",
                productService.listProducts(page, size, sortBy, sortDir)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.ok("Product fetched", productService.getProduct(productId)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Search results", productService.searchProducts(q, page, size)));
    }

    @GetMapping("/category/{slug}")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> byCategory(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Products by category",
                productService.listByCategory(slug, page, size)));
    }

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> featured(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Featured products", productService.getFeaturedProducts(page, size)));
    }
}
