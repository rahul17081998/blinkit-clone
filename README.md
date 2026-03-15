# Blinkit Clone — Backend

> **Stack:** Spring Boot 3.3.6 + Spring Cloud 2023.0.3 + MongoDB Atlas + Redis + Kafka + JWT
> **Architecture:** Microservices (Spring Cloud)

---

## Stage 1 — Infrastructure & Skeleton ✅

### Overview

Stage 1 sets up the foundational infrastructure. All services boot, register with Eureka, and the API Gateway routes requests. No business logic yet — just the skeleton that every future stage builds on.

---

### Services Built

| Service | Port | Description |
|---|---|---|
| `eureka-server` | 8761 | Service discovery — all microservices register here |
| `config-server` | 8888 | Centralised config — serves `configs/application.yml` to all services |
| `api-gateway` | 8080 | Single entry point — routes `/api/**`, JWT validation, Redis rate limiting |

### Infrastructure (Docker)

| Container | Port | Description |
|---|---|---|
| `blinkit-redis` | 6379 | Redis — cart storage, rate limiting, token blacklist |
| `blinkit-kafka` | 9092 | Kafka broker — event bus for all async communication |
| `blinkit-zookeeper` | 2181 | Zookeeper — required by Kafka |
| `blinkit-kafka-ui` | 9093 | Kafka UI — browse topics and messages |
| `blinkit-redis-ui` | 9191 | Redis Commander — browse Redis keys visually |

---

### Project Structure

```
blinkit-clone/
├── backend/
│   ├── pom.xml                   ← Parent POM (multi-module Maven, Spring Cloud BOM)
│   ├── eureka-server/            ← Service discovery (port 8761)
│   ├── config-server/            ← Centralised config (port 8888)
│   │   └── src/main/resources/
│   │       └── configs/
│   │           └── application.yml   ← Shared config for ALL services
│   └── api-gateway/              ← Spring Cloud Gateway (port 8080)
│       └── src/main/java/com/blinkit/gateway/
│           ├── filter/
│           │   └── JwtAuthFilter.java    ← Global JWT filter (validates token, injects headers)
│           └── config/
│               └── RateLimiterConfig.java ← Redis rate limiter key resolver
├── docker-compose.infra.yml      ← Infrastructure only (Redis, Kafka, Zookeeper + UIs)
├── .env                          ← All environment variables (never commit real secrets)
└── .gitignore
```

---

### API Gateway Routes

All traffic enters through the gateway at `localhost:8080`.

| Route | Forwards to | Auth required |
|---|---|---|
| `/api/auth/**` | `auth-service:8081` | No |
| `/api/users/**` | `user-service:8082` | Yes |
| `/api/products/**` | `product-service:8083` | No (GET) |
| `/api/categories/**` | `product-service:8083` | No (GET) |
| `/api/inventory/**` | `inventory-service:8084` | Yes |
| `/api/orders/**` | `order-service:8085` | Yes |
| `/api/payments/**` | `payment-service:8086` | Yes |
| `/api/cart/**` | `cart-service:8087` | Yes |
| `/api/delivery/**` | `delivery-service:8088` | Yes |
| `/api/notifications/**` | `notification-service:8089` | Yes |
| `/api/coupons/**` | `coupon-service:8090` | Yes |
| `/api/reviews/**` | `review-service:8091` | Yes |

**Public paths (no JWT required):**
```
/api/auth/login
/api/auth/signup
/api/auth/refresh
/api/auth/verify
/api/auth/forgot-password
/api/auth/reset-password
/api/products
/api/categories
/actuator
```

---

### JWT Filter

The `JwtAuthFilter` runs on every request (order = -100, highest priority):

1. If path is public → pass through
2. Extract `Authorization: Bearer <token>` header
3. Validate token signature + expiry using shared `JWT_SECRET_KEY`
4. Inject `X-User-Id` and `X-User-Role` headers into the downstream request
5. Reject with `401` if token is missing, invalid, or expired

Downstream services **do not parse JWT** — they just read `X-User-Id` and `X-User-Role` headers.

---

### Rate Limiting

Redis token-bucket rate limiter applied to **all routes**:
- **20 requests/second** per user (replenish rate)
- **Burst up to 40 requests** (burst capacity)
- Key = `X-User-Id` header for authenticated users, client IP for public routes

---

### How to Run

#### Step 1 — Start infrastructure (Docker)

```bash
docker compose -f docker-compose.infra.yml up -d
```

Wait for all containers to be healthy:
```bash
docker ps
```

Expected output:
```
blinkit-kafka-ui    Up    0.0.0.0:9093->8080/tcp
blinkit-kafka       Up (healthy)   0.0.0.0:9092->9092/tcp
blinkit-redis-ui    Up (healthy)   0.0.0.0:9191->8081/tcp
blinkit-zookeeper   Up (healthy)   0.0.0.0:2181->2181/tcp
blinkit-redis       Up (healthy)   0.0.0.0:6379->6379/tcp
```

#### Step 2 — Start Eureka Server (first — others depend on it)

```bash
EUREKA_SERVER_URL=http://localhost:8761/eureka \
java -jar backend/eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar
```

Wait until you see: `Started EurekaServerApplication`

#### Step 3 — Start Config Server

```bash
EUREKA_SERVER_URL=http://localhost:8761/eureka \
java -jar backend/config-server/target/config-server-1.0.0-SNAPSHOT.jar
```

#### Step 4 — Start API Gateway

```bash
REDIS_HOST=localhost \
REDIS_PORT=6379 \
REDIS_PASSWORD=localpassword \
EUREKA_SERVER_URL=http://localhost:8761/eureka \
JWT_SECRET_KEY=blinkit-dev-secret-key-minimum-32-chars!! \
java -jar backend/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
```

> **Tip:** Copy the env vars from `.env` and export them in your shell to avoid typing them every time.

#### Step 5 — Rebuild JARs (if you change source code)

```bash
cd backend
mvn clean package -DskipTests
```

---

### Verification

After starting all 3 services, verify everything is working:

| Check | URL | Expected |
|---|---|---|
| Eureka dashboard | `http://localhost:8761` | Shows API-GATEWAY and CONFIG-SERVER as UP |
| Eureka health | `http://localhost:8761/actuator/health` | `{"status":"UP"}` |
| Config Server health | `http://localhost:8888/actuator/health` | `{"status":"UP"}` |
| Gateway health | `http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| Kafka UI | `http://localhost:9093` | Kafka UI dashboard |
| Redis Commander | `http://localhost:9191` | Redis key browser |

**Check Eureka registered services via API:**
```bash
curl http://localhost:8761/eureka/apps
```
Should show `API-GATEWAY` and `CONFIG-SERVER` both with status `UP`.

---

### Environment Variables

All variables are defined in `.env`. Key ones for Stage 1:

| Variable | Default | Description |
|---|---|---|
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | `localpassword` | Redis password (set in docker-compose.infra.yml) |
| `EUREKA_SERVER_URL` | `http://localhost:8761/eureka` | Eureka registration URL |
| `JWT_SECRET_KEY` | `blinkit-dev-secret-key-minimum-32-chars!!` | Must be 32+ chars, same across all services |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |

> Never commit real secrets to git. The `.env` file is in `.gitignore`.

---

### Shared Config (Config Server)

The file `backend/config-server/src/main/resources/configs/application.yml` is served to **all microservices** on startup. It provides shared defaults for:
- Redis connection
- Kafka producer/consumer settings
- Eureka client registration
- Actuator endpoints
- Log level (`DEBUG` for `com.blinkit.*`)

Service-specific overrides go in `configs/{service-name}.yml` in the same folder.

---

### Stage Roadmap

| Stage | Focus | Status |
|---|---|---|
| **1** | Infrastructure & Skeleton (Eureka, Config, Gateway) | ✅ Done |
| 2 | Auth & User (JWT, OTP, password reset, user profiles) | ⏳ Next |
| 3 | Product Catalog & Inventory | — |
| 4 | Cart & Coupons | — |
| 5 | Orders & Payments (Razorpay) | — |
| 6 | Frontend (React 18 + Vite) | — |
| 7 | Delivery, Reviews & Docker | — |
