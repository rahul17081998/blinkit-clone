package com.blinkit.auth.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.blinkit.common.enums.Role;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;       // UUID — shared key across all services

    @Indexed(unique = true)
    private String email;

    private String password;     // BCrypt hashed

    @Builder.Default
    private List<Role> roles = new ArrayList<>();

    @Builder.Default
    private Boolean isVerified = false;

    @Builder.Default
    private Boolean isActive = true;

    private Instant lastLoginAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
