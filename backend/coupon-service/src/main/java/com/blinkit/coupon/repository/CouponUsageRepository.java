package com.blinkit.coupon.repository;

import com.blinkit.coupon.entity.CouponUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CouponUsageRepository extends MongoRepository<CouponUsage, String> {

    long countByCouponIdAndUserId(String couponId, String userId);

    List<CouponUsage> findByCouponId(String couponId);

    List<CouponUsage> findByUserId(String userId);
}
