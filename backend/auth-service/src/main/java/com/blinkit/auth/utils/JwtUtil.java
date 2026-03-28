package com.blinkit.auth.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.expiry-minutes}")
    private long expiryMinutes;

    public String generateAccessToken(String userId, String email, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claims(Map.of(
                        "userId", userId,
                        "email",  email,
                        "role",   role
                ))
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMinutes * 60 * 1000))
                .signWith(getKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public long getExpiryMinutes() {
        return expiryMinutes;
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
