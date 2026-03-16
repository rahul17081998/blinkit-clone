package com.blinkit.inventory.dto.response;

import com.blinkit.inventory.entity.Stock;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class StockResponse {
    private String productId;
    private String productName;
    private Integer availableQty;
    private Integer reservedQty;
    private Integer totalQty;
    private Integer lowStockThreshold;
    private String unit;
    private Instant lastRestockedAt;
    private Instant updatedAt;

    public static StockResponse from(Stock s) {
        return StockResponse.builder()
                .productId(s.getProductId())
                .productName(s.getProductName())
                .availableQty(s.getAvailableQty())
                .reservedQty(s.getReservedQty())
                .totalQty(s.getTotalQty())
                .lowStockThreshold(s.getLowStockThreshold())
                .unit(s.getUnit())
                .lastRestockedAt(s.getLastRestockedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
