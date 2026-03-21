package com.blinkit.cart.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds X-Internal-Secret to all outgoing Feign requests so downstream
 * services (product-service, coupon-service) accept them through
 * their InternalRequestFilter.
 */
@Configuration
public class FeignConfig {

    @Value("${internal.secret-key}")
    private String internalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return requestTemplate -> requestTemplate.header("X-Internal-Secret", internalSecret);
    }
}
