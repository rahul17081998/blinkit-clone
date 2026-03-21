package com.blinkit.cart.client;

import com.blinkit.cart.client.dto.ProductApiResponse;
import com.blinkit.cart.client.fallback.ProductServiceFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", fallback = ProductServiceFallback.class)
public interface ProductServiceClient {

    @GetMapping("/products/{productId}")
    ProductApiResponse getProduct(@PathVariable("productId") String productId);
}
