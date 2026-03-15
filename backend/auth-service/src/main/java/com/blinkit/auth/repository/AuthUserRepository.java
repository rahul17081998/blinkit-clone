package com.blinkit.auth.repository;

import com.blinkit.auth.entity.AuthUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AuthUserRepository extends MongoRepository<AuthUser, String> {
    Optional<AuthUser> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<AuthUser> findByUserId(String userId);
}
