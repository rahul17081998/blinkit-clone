package com.blinkit.inventory.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stock_movements")
public class StockMovement {

    @Id
    private String id;

    @Indexed
    private String productId;

    private String type;  // RESTOCK, RESERVE, RELEASE, SALE, ADJUSTMENT, RETURN
    private Integer quantity;
    private Integer previousAvailableQty;
    private Integer newAvailableQty;
    private String orderId;
    private String reason;
    private String performedBy;  // admin userId or "SYSTEM"

    @CreatedDate
    private Instant createdAt;
}
