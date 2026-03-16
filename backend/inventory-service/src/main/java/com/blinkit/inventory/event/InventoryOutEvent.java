package com.blinkit.inventory.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryOutEvent {
    public static final String TOPIC = "inventory.out";

    private String productId;
    private String productName;
}
