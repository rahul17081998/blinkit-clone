package com.blinkit.review.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.review.dto.request.ReviewRequest;
import com.blinkit.review.dto.response.ProductRatingSummary;
import com.blinkit.review.dto.response.ReviewResponse;
import com.blinkit.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // ── Customer endpoints ─────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody ReviewRequest req) {
        requireCustomer(role);
        ReviewResponse response = reviewService.submitReview(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Review submitted", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getMyReviews(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        requireCustomer(role);
        return ResponseEntity.ok(ApiResponse.ok("Reviews fetched", reviewService.getMyReviews(userId)));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable String reviewId) {
        reviewService.deleteReview(userId, role, reviewId);
        return ResponseEntity.ok(ApiResponse.ok("Review deleted"));
    }

    // ── Public endpoints ───────────────────────────────────────────

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getProductReviews(
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("Reviews fetched",
                reviewService.getProductReviews(productId, pageable)));
    }

    @GetMapping("/product/{productId}/summary")
    public ResponseEntity<ApiResponse<ProductRatingSummary>> getProductRatingSummary(
            @PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.ok("Rating summary fetched",
                reviewService.getProductRatingSummary(productId)));
    }

    // ── Admin endpoints ────────────────────────────────────────────

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getAllReviews(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("Reviews fetched", reviewService.getAllReviews(pageable)));
    }

    // ── Helper ─────────────────────────────────────────────────────

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private void requireCustomer(String role) {
        if (!"CUSTOMER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer access required");
        }
    }
}
