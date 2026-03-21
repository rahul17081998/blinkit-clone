package com.blinkit.cart.client.fallback;

import com.blinkit.cart.client.CouponServiceClient;
import com.blinkit.cart.client.dto.ValidateCouponRequest;
import com.blinkit.cart.client.dto.ValidateCouponResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CouponServiceFallback implements CouponServiceClient {

    @Override
    public ValidateCouponResponse validate(ValidateCouponRequest req) {
        log.warn("coupon-service unavailable — fallback for code={}", req.getCode());
        ValidateCouponResponse resp = new ValidateCouponResponse();
        resp.setValid(false);
        resp.setDiscountAmount(0.0);
        resp.setFreeDelivery(false);
        resp.setMessage("Coupon service temporarily unavailable");
        return resp;
    }
}
