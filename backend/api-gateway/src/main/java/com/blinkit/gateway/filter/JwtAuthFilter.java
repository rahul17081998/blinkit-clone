package com.blinkit.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Global JWT authentication filter.
 *
 * Runs on EVERY request before it reaches any downstream service.
 *
 * What it does:
 *  1. Checks if the request path is public (no token needed) → passes through
 *  2. Extracts Bearer token from Authorization header
 *  3. Validates token signature + expiry using the shared JWT secret
 *  4. Injects X-User-Id and X-User-Role headers so downstream services
 *     don't need to parse JWT themselves — they just read the headers
 *  5. Rejects invalid/missing tokens with 401
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret-key}")
    private String secretKey;

    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static final String BLACKLIST_PREFIX = "blacklist:";

    // Routes that do NOT require a JWT token
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/signup",
            "/api/auth/refresh",
            "/api/auth/verify",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/products",
            "/api/categories",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Step 1 — skip auth for public routes
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Step 2 — extract Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorised(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // Step 3 — validate token and extract claims
        Claims claims;
        try {
            claims = extractClaims(token);
        } catch (Exception e) {
            return unauthorised(exchange, "Invalid or expired token: " + e.getMessage());
        }

        String userId = claims.get("userId", String.class);
        String email  = claims.get("email",  String.class);
        String role   = claims.get("role",   String.class);

        // Step 4 — check token blacklist (set on logout)
        return redisTemplate.hasKey(BLACKLIST_PREFIX + token)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return unauthorised(exchange, "Token has been invalidated");
                    }
                    // Step 5 — inject user context headers for downstream services
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(r -> r
                                    .header("X-User-Id",    userId)
                                    .header("X-User-Role",  role)
                                    .header("X-User-Email", email != null ? email : ""))
                            .build();
                    return chain.filter(mutatedExchange);
                });
    }

    private Claims extractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorised(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", reason);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Run before all other filters (lower number = higher priority)
        return -100;
    }
}
