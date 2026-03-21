package com.blinkit.order.client;

import com.blinkit.order.client.dto.CartApiResponse;
import com.blinkit.order.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "cart-service", configuration = FeignConfig.class)
public interface CartServiceClient {

    @GetMapping("/cart")
    CartApiResponse getCart(@RequestHeader("X-User-Id") String userId);
}
