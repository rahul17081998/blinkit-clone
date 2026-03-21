package com.blinkit.cart.client.fallback;

import com.blinkit.cart.client.ProductServiceClient;
import com.blinkit.cart.client.dto.ProductApiResponse;
import com.blinkit.cart.client.dto.ProductDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductServiceFallback implements ProductServiceClient {

    @Override
    public ProductApiResponse getProduct(String productId) {
        log.warn("product-service unavailable — fallback for productId={}", productId);
        ProductDto unavailable = new ProductDto();
        unavailable.setProductId(productId);
        unavailable.setIsAvailable(false);

        ProductApiResponse resp = new ProductApiResponse();
        resp.setSuccess(false);
        resp.setMessage("Product service unavailable");
        resp.setData(unavailable);
        return resp;
    }
}
