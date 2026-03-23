package com.blinkit.review.service;

import com.blinkit.review.client.OrderServiceClient;
import com.blinkit.review.dto.request.ReviewRequest;
import com.blinkit.review.dto.response.ProductRatingSummary;
import com.blinkit.review.dto.response.ReviewResponse;
import com.blinkit.review.entity.Review;
import com.blinkit.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderServiceClient orderServiceClient;

    /**
     * Submit or update a review. Idempotent — if user already reviewed this product, update it.
     * Requires the user to have a DELIVERED order containing the product.
     */
    public ReviewResponse submitReview(String userId, ReviewRequest req) {
        verifyPurchased(userId, req.getProductId());

        Review existing = reviewRepository.findByUserIdAndProductId(userId, req.getProductId())
                .orElse(null);

        if (existing != null) {
            existing.setRating(req.getRating());
            existing.setTitle(req.getTitle());
            existing.setComment(req.getComment());
            reviewRepository.save(existing);
            log.info("Updated review {} for userId={} productId={}", existing.getReviewId(), userId, req.getProductId());
            return ReviewResponse.from(existing);
        }

        Review review = Review.builder()
                .reviewId(UUID.randomUUID().toString())
                .userId(userId)
                .productId(req.getProductId())
                .rating(req.getRating())
                .title(req.getTitle())
                .comment(req.getComment())
                .build();
        reviewRepository.save(review);
        log.info("Created review {} for userId={} productId={}", review.getReviewId(), userId, req.getProductId());
        return ReviewResponse.from(review);
    }

    public Page<ReviewResponse> getProductReviews(String productId, Pageable pageable) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(ReviewResponse::from);
    }

    public ProductRatingSummary getProductRatingSummary(String productId) {
        List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(
                productId, Pageable.unpaged()).getContent();
        OptionalDouble avg = reviews.stream().mapToInt(Review::getRating).average();
        double averageRating = avg.isPresent() ? Math.round(avg.getAsDouble() * 10.0) / 10.0 : 0.0;
        return new ProductRatingSummary(productId, averageRating, reviews.size());
    }

    public List<ReviewResponse> getMyReviews(String userId) {
        return reviewRepository.findByUserId(userId).stream()
                .map(ReviewResponse::from)
                .collect(Collectors.toList());
    }

    public Page<ReviewResponse> getAllReviews(Pageable pageable) {
        return reviewRepository.findAll(pageable).map(ReviewResponse::from);
    }

    public void deleteReview(String userId, String role, String reviewId) {
        Review review = reviewRepository.findByReviewId(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isAdmin && !review.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own reviews");
        }

        reviewRepository.delete(review);
        log.info("Deleted review {} by userId={} (role={})", reviewId, userId, role);
    }

    private void verifyPurchased(String userId, String productId) {
        try {
            boolean ordered = orderServiceClient.hasOrdered(userId, productId).hasOrdered();
            if (!ordered) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You can only review products you have purchased and received");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify purchase for userId={} productId={}: {}", userId, productId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to verify purchase at this time. Please try again.");
        }
    }
}
