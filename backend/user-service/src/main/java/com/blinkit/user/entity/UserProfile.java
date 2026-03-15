package com.blinkit.user.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Document(collection = "user_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;       // Same UUID as AuthUser.userId

    private String firstName;
    private String lastName;

    @Indexed(unique = true)
    private String email;        // Denormalized from auth-service

    private String phone;
    private String profileImageUrl;
    private LocalDate dateOfBirth;
    private String gender;       // MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY

    @Builder.Default
    private Boolean isPhoneVerified = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
