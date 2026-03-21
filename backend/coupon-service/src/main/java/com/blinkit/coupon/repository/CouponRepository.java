package com.blinkit.coupon.repository;

import com.blinkit.coupon.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends MongoRepository<Coupon, String> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    List<Coupon> findByIsActiveTrueAndValidFromBeforeAndValidUntilAfter(Instant now1, Instant now2);

    Page<Coupon> findAll(Pageable pageable);
}
