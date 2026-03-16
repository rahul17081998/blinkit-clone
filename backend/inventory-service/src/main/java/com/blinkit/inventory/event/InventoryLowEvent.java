package com.blinkit.inventory.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLowEvent {
    public static final String TOPIC = "inventory.low";

    private String productId;
    private String productName;
    private Integer availableQty;
    private Integer lowStockThreshold;
}
