package com.blinkit.delivery.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "delivery_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTask {

    @Id
    private String id;

    private String taskId;          // UUID

    @Indexed(unique = true)
    private String orderId;

    private String userId;          // customer userId

    private String addressId;       // customer addressId (UUID from user-service)

    private String deliveryPartnerId;  // null until assigned

    @Builder.Default
    private String status = "UNASSIGNED";  // UNASSIGNED → QUEUED → ASSIGNED → PICKED_UP → OUT_FOR_DELIVERY → DELIVERED | FAILED | CANCELLED
    // UNASSIGNED : just created, deciding whether to assign directly or queue
    // QUEUED     : waiting in priority queue (ordered by createdAt ASC — oldest served first)
    // ASSIGNED   : delivery partner assigned, heading to store

    // Store snapshot — name & address of the dark store
    private String storeName;
    private String storeAddress;
    private Double storeLat;
    private Double storeLng;

    // Live rider location (updated by partner)
    private Double currentLat;
    private Double currentLng;

    private Instant estimatedDeliveryAt;
    private Instant actualPickupAt;
    private Instant actualDeliveryAt;

    // Set to now + random(min,max) when entering ASSIGNED / PICKED_UP / OUT_FOR_DELIVERY.
    // Simulation scheduler advances the task only after this time has passed.
    private Instant nextStatusAdvanceAt;

    private String failureReason;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
