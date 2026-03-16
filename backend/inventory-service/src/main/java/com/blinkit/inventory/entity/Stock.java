package com.blinkit.inventory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stock")
public class Stock {

    @Id
    private String id;

    @Indexed(unique = true)
    private String productId;

    private String productName;
    private Integer availableQty;
    private Integer reservedQty;
    private Integer totalQty;
    private Integer lowStockThreshold;
    private String unit;
    private Instant lastRestockedAt;

    @Version
    private Long version;  // optimistic locking

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
