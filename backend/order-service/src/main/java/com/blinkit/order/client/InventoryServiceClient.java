package com.blinkit.order.client;

import com.blinkit.order.client.dto.ReserveStockRequest;
import com.blinkit.order.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", configuration = FeignConfig.class)
public interface InventoryServiceClient {

    @PostMapping("/inventory/reserve")
    void reserveStock(@RequestBody ReserveStockRequest req);

    @PostMapping("/inventory/release")
    void releaseStock(@RequestBody ReserveStockRequest req);

    @PostMapping("/inventory/confirm")
    void confirmStock(@RequestBody ReserveStockRequest req);
}
