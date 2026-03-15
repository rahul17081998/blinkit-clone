package com.blinkit.user.repository;

import com.blinkit.user.entity.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserProfileRepository extends MongoRepository<UserProfile, String> {
    Optional<UserProfile> findByUserId(String userId);
    boolean existsByUserId(String userId);
}
