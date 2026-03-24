package com.blinkit.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${internal.secret-key}")
    private String internalSecret;

    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static final String BLACKLIST_PREFIX = "blacklist:";

    // Routes that do NOT require a JWT token (any HTTP method)
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/signup",
            "/api/auth/refresh",
            "/api/auth/verify",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/actuator"
    );

    // Routes that are public only for GET requests (catalog browsing + promo banner)
    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/products",
            "/api/categories",
            "/api/coupons/active",
            "/api/reviews/product"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.info("path: "+path);

        // Step 1 — skip auth for public routes but still inject internal secret
        if (isPublicPath(path, exchange.getRequest().getMethod())) {
            ServerWebExchange publicExchange = exchange.mutate()
                    .request(r -> r.header("X-Internal-Secret", internalSecret))
                    .build();
            return chain.filter(publicExchange);
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
            log.warn("JWT validation failed. Secret prefix: '{}'. Error: {}", secretKey.substring(0, Math.min(10, secretKey.length())), e.getMessage());
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
                    // Step 5 — inject user context + internal secret headers for downstream services
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(r -> r
                                    .header("X-User-Id",        userId)
                                    .header("X-User-Role",      role)
                                    .header("X-User-Email",     email != null ? email : "")
                                    .header("X-Internal-Secret", internalSecret))
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

    private boolean isPublicPath(String path, org.springframework.http.HttpMethod method) {
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) return true;
        if (org.springframework.http.HttpMethod.GET.equals(method) &&
            PUBLIC_GET_PATHS.stream().anyMatch(path::startsWith)) return true;
        return false;
    }

    private Mono<Void> unauthorised(ServerWebExchange exchange, String reason) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"message\":\"" + reason.replace("\"", "'") + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Run before all other filters (lower number = higher priority)
        return -100;
    }
}
