package com.blinkit.delivery.dto.response;

import com.blinkit.delivery.entity.DeliveryTask;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DeliveryTaskResponse {

    private String taskId;
    private String orderId;
    private String userId;
    private String addressId;
    private String deliveryPartnerId;
    private String partnerName;
    private String partnerPhone;
    private String vehicleType;
    private String vehicleNumber;
    private String status;
    private String storeName;
    private String storeAddress;
    private Double storeLat;
    private Double storeLng;
    private Double currentLat;
    private Double currentLng;
    private Instant estimatedDeliveryAt;
    private Instant actualPickupAt;
    private Instant actualDeliveryAt;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public static DeliveryTaskResponse from(DeliveryTask t) {
        return DeliveryTaskResponse.builder()
                .taskId(t.getTaskId())
                .orderId(t.getOrderId())
                .userId(t.getUserId())
                .addressId(t.getAddressId())
                .deliveryPartnerId(t.getDeliveryPartnerId())
                .status(t.getStatus())
                .storeName(t.getStoreName())
                .storeAddress(t.getStoreAddress())
                .storeLat(t.getStoreLat())
                .storeLng(t.getStoreLng())
                .currentLat(t.getCurrentLat())
                .currentLng(t.getCurrentLng())
                .estimatedDeliveryAt(t.getEstimatedDeliveryAt())
                .actualPickupAt(t.getActualPickupAt())
                .actualDeliveryAt(t.getActualDeliveryAt())
                .failureReason(t.getFailureReason())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
