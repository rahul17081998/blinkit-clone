package com.blinkit.order.entity;

import com.blinkit.common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;

    @Indexed(unique = true)
    private String orderNumber;   // BLK-YYYYMMDD-NNNN

    @Indexed
    private String userId;

    private String addressId;

    private List<OrderItem> items;

    private String couponCode;
    private double couponDiscount;
    private double itemsTotal;
    private double deliveryFee;
    private double totalAmount;

    private OrderStatus status;

    private String paymentId;     // transactionId from payment-service
    private String notes;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
