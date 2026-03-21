package com.blinkit.cart.client;

import com.blinkit.cart.client.dto.ValidateCouponRequest;
import com.blinkit.cart.client.dto.ValidateCouponResponse;
import com.blinkit.cart.client.fallback.CouponServiceFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "coupon-service", fallback = CouponServiceFallback.class)
public interface CouponServiceClient {

    @PostMapping("/coupons/validate")
    ValidateCouponResponse validate(@RequestBody ValidateCouponRequest req);
}
