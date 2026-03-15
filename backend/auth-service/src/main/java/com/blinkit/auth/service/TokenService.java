package com.blinkit.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate redis;
    private static final String REFRESH_PREFIX   = "refresh:";
    private static final String BLACKLIST_PREFIX  = "blacklist:";

    @Value("${jwt.refresh-expiry-days:30}")
    private long refreshExpiryDays;

    // ── Refresh tokens ────────────────────────────────────────────

    public String createRefreshToken(String userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(REFRESH_PREFIX + userId, token, refreshExpiryDays, TimeUnit.DAYS);
        return token;
    }

    public boolean validateRefreshToken(String userId, String token) {
        String stored = redis.opsForValue().get(REFRESH_PREFIX + userId);
        return token.equals(stored);
    }

    public void deleteRefreshToken(String userId) {
        redis.delete(REFRESH_PREFIX + userId);
    }

    // ── Blacklist (on logout) ─────────────────────────────────────

    public void blacklistToken(String accessToken, long ttlSeconds) {
        redis.opsForValue().set(BLACKLIST_PREFIX + accessToken, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + accessToken));
    }
}
