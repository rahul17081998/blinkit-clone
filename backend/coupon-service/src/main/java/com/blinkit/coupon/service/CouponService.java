package com.blinkit.coupon.service;

import com.blinkit.common.enums.CouponType;
import com.blinkit.coupon.dto.request.CreateCouponRequest;
import com.blinkit.coupon.dto.request.RecordUsageRequest;
import com.blinkit.coupon.dto.request.UpdateCouponRequest;
import com.blinkit.coupon.dto.request.ValidateCouponRequest;
import com.blinkit.coupon.dto.response.CouponResponse;
import com.blinkit.coupon.dto.response.CouponUsageStatsResponse;
import com.blinkit.coupon.dto.response.ValidateCouponResponse;
import com.blinkit.coupon.entity.Coupon;
import com.blinkit.coupon.entity.CouponUsage;
import com.blinkit.coupon.repository.CouponRepository;
import com.blinkit.coupon.repository.CouponUsageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String COUPON_CACHE_PREFIX = "coupon:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // ── Admin: Create ─────────────────────────────────────────────────

    public CouponResponse createCoupon(CreateCouponRequest req) {
        String code = req.getCode().toUpperCase().trim();

        if (couponRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Coupon with code '" + code + "' already exists");
        }

        validateCouponFields(req.getType(), req.getValue(), req.getMaxDiscount());

        Coupon coupon = Coupon.builder()
                .code(code)
                .type(req.getType())
                .value(req.getValue())
                .maxDiscount(req.getMaxDiscount())
                .minOrderAmount(req.getMinOrderAmount())
                .usageLimit(req.getUsageLimit())
                .usedCount(0)
                .perUserLimit(req.getPerUserLimit())
                .validFrom(req.getValidFrom())
                .validUntil(req.getValidUntil())
                .isActive(req.getIsActive())
                .build();

        Coupon saved = couponRepository.save(coupon);
        log.info("Coupon created: code={}, type={}", code, req.getType());
        return CouponResponse.from(saved);
    }

    // ── Admin: Get all ────────────────────────────────────────────────

    public Page<CouponResponse> getAllCoupons(Pageable pageable) {
        return couponRepository.findAll(pageable).map(CouponResponse::from);
    }

    // ── Admin: Get by ID ──────────────────────────────────────────────

    public CouponResponse getCouponById(String id) {
        return CouponResponse.from(findById(id));
    }

    // ── Admin: Update ─────────────────────────────────────────────────

    public CouponResponse updateCoupon(String id, UpdateCouponRequest req) {
        Coupon coupon = findById(id);

        if (req.getType() != null) coupon.setType(req.getType());
        if (req.getValue() != null) coupon.setValue(req.getValue());
        if (req.getMaxDiscount() != null) coupon.setMaxDiscount(req.getMaxDiscount());
        if (req.getMinOrderAmount() != null) coupon.setMinOrderAmount(req.getMinOrderAmount());
        if (req.getUsageLimit() != null) coupon.setUsageLimit(req.getUsageLimit());
        if (req.getPerUserLimit() != null) coupon.setPerUserLimit(req.getPerUserLimit());
        if (req.getValidFrom() != null) coupon.setValidFrom(req.getValidFrom());
        if (req.getValidUntil() != null) coupon.setValidUntil(req.getValidUntil());
        if (req.getIsActive() != null) coupon.setIsActive(req.getIsActive());

        Coupon saved = couponRepository.save(coupon);
        evictCache(coupon.getCode());
        log.info("Coupon updated: id={}", id);
        return CouponResponse.from(saved);
    }

    // ── Admin: Delete ─────────────────────────────────────────────────

    public void deleteCoupon(String id) {
        Coupon coupon = findById(id);
        couponRepository.deleteById(id);
        evictCache(coupon.getCode());
        log.info("Coupon deleted: id={}, code={}", id, coupon.getCode());
    }

    // ── Admin: Usage stats ────────────────────────────────────────────

    public CouponUsageStatsResponse getUsageStats(String id) {
        Coupon coupon = findById(id);
        long totalUsed = couponUsageRepository.findByCouponId(id).size();
        Integer remaining = coupon.getUsageLimit() != null
                ? Math.max(0, coupon.getUsageLimit() - (int) totalUsed)
                : null;

        return CouponUsageStatsResponse.builder()
                .couponId(id)
                .couponCode(coupon.getCode())
                .totalUsed(totalUsed)
                .usageLimit(coupon.getUsageLimit())
                .remaining(remaining)
                .build();
    }

    // ── Public: Active coupons ────────────────────────────────────────

    public List<CouponResponse> getActiveCoupons() {
        Instant now = Instant.now();
        return couponRepository.findByIsActiveTrueAndValidFromBeforeAndValidUntilAfter(now, now)
                .stream()
                .map(CouponResponse::from)
                .collect(Collectors.toList());
    }

    // ── Internal: Validate ────────────────────────────────────────────

    public ValidateCouponResponse validate(ValidateCouponRequest req) {
        String code = req.getCode().toUpperCase().trim();

        Coupon coupon = getCouponFromCache(code);
        if (coupon == null) {
            coupon = couponRepository.findByCode(code).orElse(null);
            if (coupon == null) {
                return ValidateCouponResponse.invalid("Coupon not found");
            }
            putCouponInCache(code, coupon);
        }

        // Active check
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            return ValidateCouponResponse.invalid("Coupon is not active");
        }

        // Date range check
        Instant now = Instant.now();
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidUntil())) {
            return ValidateCouponResponse.invalid("Coupon has expired or is not yet active");
        }

        // Min order amount check
        if (req.getCartTotal() < coupon.getMinOrderAmount()) {
            return ValidateCouponResponse.invalid(
                    "Minimum order amount of ₹" + coupon.getMinOrderAmount() + " required");
        }

        // Global usage limit check
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            return ValidateCouponResponse.invalid("Coupon usage limit has been reached");
        }

        // Per-user usage check
        long userUsageCount = couponUsageRepository.countByCouponIdAndUserId(coupon.getId(), req.getUserId());
        if (userUsageCount >= coupon.getPerUserLimit()) {
            return ValidateCouponResponse.invalid("You have already used this coupon");
        }

        // Calculate discount
        return calculateDiscount(coupon, req.getCartTotal());
    }

    // ── Internal: Record usage (called by order-service in Stage 5) ──

    public void recordUsage(RecordUsageRequest req) {
        String code = req.getCouponCode().toUpperCase().trim();
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));

        CouponUsage usage = CouponUsage.builder()
                .couponId(coupon.getId())
                .couponCode(code)
                .userId(req.getUserId())
                .orderId(req.getOrderId())
                .build();
        couponUsageRepository.save(usage);

        // Increment global usedCount
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.save(coupon);
        evictCache(code);

        log.info("Coupon usage recorded: code={}, userId={}, orderId={}", code, req.getUserId(), req.getOrderId());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private ValidateCouponResponse calculateDiscount(Coupon coupon, double cartTotal) {
        switch (coupon.getType()) {
            case FLAT -> {
                double discount = Math.min(coupon.getValue(), cartTotal);
                return ValidateCouponResponse.valid(discount, false,
                        "₹" + coupon.getValue() + " off applied");
            }
            case PERCENT -> {
                double raw = cartTotal * coupon.getValue() / 100.0;
                double discount = coupon.getMaxDiscount() != null
                        ? Math.min(raw, coupon.getMaxDiscount())
                        : raw;
                String msg = coupon.getValue().intValue() + "% off applied" +
                        (coupon.getMaxDiscount() != null ? " (max ₹" + coupon.getMaxDiscount().intValue() + ")" : "");
                return ValidateCouponResponse.valid(Math.round(discount * 100.0) / 100.0, false, msg);
            }
            case FIRST_ORDER -> {
                // TODO: verify via order-service Feign in Stage 5 that this is user's first order
                double discount = Math.min(coupon.getValue() != null ? coupon.getValue() : 0.0, cartTotal);
                return ValidateCouponResponse.valid(discount, false, "First order discount applied");
            }
            case FREE_DELIVERY -> {
                return ValidateCouponResponse.valid(0.0, true, "Free delivery applied");
            }
            default -> {
                return ValidateCouponResponse.invalid("Unknown coupon type");
            }
        }
    }

    private void validateCouponFields(CouponType type, Double value, Double maxDiscount) {
        if (type == CouponType.FLAT || type == CouponType.PERCENT || type == CouponType.FIRST_ORDER) {
            if (value == null || value <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "value is required and must be positive for type " + type);
            }
        }
    }

    private Coupon findById(String id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
    }

    private Coupon getCouponFromCache(String code) {
        try {
            String cached = redisTemplate.opsForValue().get(COUPON_CACHE_PREFIX + code);
            if (cached != null) {
                return objectMapper.readValue(cached, Coupon.class);
            }
        } catch (Exception e) {
            log.warn("Cache read failed for coupon {}: {}", code, e.getMessage());
        }
        return null;
    }

    private void putCouponInCache(String code, Coupon coupon) {
        try {
            String json = objectMapper.writeValueAsString(coupon);
            redisTemplate.opsForValue().set(COUPON_CACHE_PREFIX + code, json, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Cache write failed for coupon {}: {}", code, e.getMessage());
        }
    }

    private void evictCache(String code) {
        redisTemplate.delete(COUPON_CACHE_PREFIX + code);
    }
}
