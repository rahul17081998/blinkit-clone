package com.blinkit.order.client;

import com.blinkit.order.client.dto.RecordUsageRequest;
import com.blinkit.order.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "coupon-service", configuration = FeignConfig.class)
public interface CouponServiceClient {

    @PostMapping("/coupons/usage")
    void recordUsage(@RequestBody RecordUsageRequest req);
}
