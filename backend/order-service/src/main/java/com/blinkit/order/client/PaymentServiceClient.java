package com.blinkit.order.client;

import com.blinkit.order.client.dto.PayRequest;
import com.blinkit.order.client.dto.PaymentApiResponse;
import com.blinkit.order.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", configuration = FeignConfig.class)
public interface PaymentServiceClient {

    @PostMapping("/payments/internal/pay")
    PaymentApiResponse pay(@RequestBody PayRequest req);

    @PostMapping("/payments/internal/refund/{orderId}")
    PaymentApiResponse refund(@PathVariable("orderId") String orderId);
}
