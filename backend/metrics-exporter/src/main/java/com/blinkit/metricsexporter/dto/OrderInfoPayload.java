package com.blinkit.metricsexporter.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Snapshot of one order's full status — stored in the OrderInfoCache map.
 * Fields become Prometheus gauge tags; this drives the Grafana table view.
 */
@Data
@Builder
public class OrderInfoPayload {

    String orderId;
    String orderNumber;      // BLK-YYYYMMDD-NNNN  (human-readable)
    String userId;

    String orderStatus;      // PENDING | CONFIRMED | DELIVERED | CANCELLED
    String paymentStatus;    // SUCCESS | PENDING | FAILED | NO_PAYMENT
    String deliveryStatus;   // UNASSIGNED | ASSIGNED | PICKED_UP | OUT_FOR_DELIVERY | DELIVERED | FAILED | NO_TASK

    double totalAmount;
    long   deliveryMinutes;  // time from order creation → actual delivery (or → now if still ongoing)
    String createdAt;        // formatted: "yyyy-MM-dd HH:mm" in IST
}
