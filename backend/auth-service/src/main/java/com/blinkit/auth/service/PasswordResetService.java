package com.blinkit.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final StringRedisTemplate redis;
    private static final String PREFIX = "pwd_reset:";

    @Value("${password-reset.expiry-minutes:15}")
    private long expiryMinutes;

    public String generateToken(String userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(PREFIX + token, userId, expiryMinutes, TimeUnit.MINUTES);
        return token;
    }

    public String getUserId(String token) {
        return redis.opsForValue().get(PREFIX + token);
    }

    public void deleteToken(String token) {
        redis.delete(PREFIX + token);
    }
}
