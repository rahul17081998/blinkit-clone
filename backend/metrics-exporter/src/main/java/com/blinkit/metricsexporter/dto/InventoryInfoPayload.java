package com.blinkit.metricsexporter.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryInfoPayload {
    String productId;
    String productName;
    long   availableQty;
    long   reservedQty;
    String stockStatus;       // IN_STOCK | LOW_STOCK | OUT_OF_STOCK
    long   lowStockThreshold;
    String isAvailable;    // "true" | "false" | "NA"
    String lastRestocked;  // "yyyy-MM-dd HH:mm" IST | "NA"
}
