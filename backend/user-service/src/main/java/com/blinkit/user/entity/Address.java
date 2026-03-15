package com.blinkit.user.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "addresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Id
    private String id;

    private String addressId;    // UUID

    @Indexed
    private String userId;       // Foreign key → UserProfile.userId

    private String label;        // HOME, WORK, OTHER
    private String recipientName;
    private String recipientPhone;
    private String flatNo;
    private String building;
    private String street;
    private String area;
    private String city;
    private String state;
    private String pincode;
    private String landmark;
    private Double lat;
    private Double lng;

    @Builder.Default
    private Boolean isDefault = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
