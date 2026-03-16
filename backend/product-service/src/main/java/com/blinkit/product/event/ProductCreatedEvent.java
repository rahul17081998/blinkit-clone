package com.blinkit.product.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreatedEvent {
    public static final String TOPIC = "product.created";

    private String productId;
    private String productName;
    private String unit;
}
