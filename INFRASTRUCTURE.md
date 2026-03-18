# Blinkit Clone — Infrastructure Guide

> Covers every infrastructure component: what it is, why it exists, how it fits into the system, and how to start/stop everything.

---

## Table of Contents

1. [Infrastructure Stack Overview](#1-infrastructure-stack-overview)
2. [Container Runtime — Colima](#2-container-runtime--colima)
3. [Redis](#3-redis)
4. [Apache Kafka](#4-apache-kafka)
5. [Zookeeper](#5-zookeeper)
6. [Kafka UI & Redis Commander](#6-kafka-ui--redis-commander)
7. [Shell Scripts](#7-shell-scripts)
8. [How to Start Everything](#8-how-to-start-everything)
9. [How to Stop Everything](#9-how-to-stop-everything)
10. [Troubleshooting](#10-troubleshooting)
11. [Quick Reference URLs](#11-quick-reference-urls)

---

## 1. Infrastructure Stack Overview

| Container | Port | Technology | Purpose |
|---|---|---|---|
| `blinkit-redis` | 6379 | Redis | Token storage, OTP, rate limiting, cart |
| `blinkit-redis-ui` | 9191 | Redis Commander | Browser UI to inspect Redis keys |
| `blinkit-zookeeper` | 2181 | Zookeeper | Internal coordinator for Kafka |
| `blinkit-kafka` | 9092 | Apache Kafka | Async event bus between services |
| `blinkit-kafka-ui` | 9093 | Kafka UI (Provectus) | Browser UI to browse topics + messages |

All containers are defined in `backend/docker-compose.infra.yml` and run on a shared Docker bridge network (`blinkit-network`).

**Container runtime on macOS:** Colima (NOT Docker Desktop).

---

## 2. Container Runtime — Colima

### What is Colima?
Colima is a lightweight container runtime for macOS. It runs a Linux VM under the hood and provides the Docker socket that `docker` and `docker compose` commands connect to.

### Why Colima instead of Docker Desktop?
- Lightweight and free — no licensing restrictions
- Provides the same Docker socket at `unix:///Users/rahulkumar/.colima/default/docker.sock`
- All `docker` and `docker compose` commands work identically

### How Colima fits in the startup flow
```
colima start
    ↓
Starts Linux VM + Docker daemon inside it
    ↓
Docker socket becomes available at unix:///.../.colima/default/docker.sock
    ↓
docker compose commands can now reach the daemon
    ↓
docker compose -f docker-compose.infra.yml up -d
    ↓
Redis, Kafka, Zookeeper containers start inside the VM
```

### Colima commands
```bash
colima start         # start the VM + Docker daemon
colima stop          # stop the VM (containers are stopped too)
colima status        # check if running
colima delete        # delete the VM entirely (nuclear option)
```

---

## 3. Redis

### What is Redis?
An in-memory key-value store. All reads and writes happen in under 1ms because data lives in RAM.

### Why Redis (not MongoDB) for these use cases?
| Need | MongoDB | Redis |
|---|---|---|
| Sub-millisecond reads | Too slow (disk I/O) | In-memory, <1ms |
| Auto-expiring keys | Needs TTL index + background cron | Native TTL — key deletes itself |
| Atomic counters (rate limiting) | Not suitable at this speed | Built-in atomic INCR |

### How Blinkit uses Redis

| Key Pattern | Value | TTL | Set by | Used by |
|---|---|---|---|---|
| `otp:{email}` | 6-digit OTP string | 5 min | auth-service (signup) | auth-service (verify) |
| `refresh:{userId}` | UUID refresh token | 30 days | auth-service (login) | auth-service (refresh, logout) |
| `blacklist:{accessToken}` | "1" | remaining token lifetime | auth-service (logout) | api-gateway (every request) |
| `pwd_reset:{token}` | userId | 15 min | auth-service (forgot-password) | auth-service (reset-password) |
| `cart:{userId}` | cart JSON | no expiry | cart-service | cart-service |
| `rate_limiter:*` | token bucket counters | rolling window | api-gateway | api-gateway |

### OTP flow example
```
1. User signs up
2. auth-service: SET otp:user@email.com 482910 EX 300   (TTL = 5 min)
3. notification-service sends OTP email via Kafka
4. User submits OTP → auth-service: GET otp:user@email.com
5. Match → verify user, DEL otp:user@email.com
6. No match or expired → 400 Bad Request
```
No database row, no cron job — Redis handles it automatically.

### Redis connection config
```
Host:     localhost (or REDIS_HOST from .env)
Port:     6379
Password: localpassword (or REDIS_PASSWORD from .env)
```

### Redis CLI debugging
```bash
redis-cli -a localpassword keys "*"
redis-cli -a localpassword get "otp:user@example.com"
redis-cli -a localpassword ttl "refresh:some-user-id"
redis-cli -a localpassword keys "blacklist:*"
```

---

## 4. Apache Kafka

### What is Kafka?
A distributed event streaming platform. Services publish events to named **topics**. Other services subscribe to those topics and consume events asynchronously — without knowing who published them.

### Why Kafka instead of direct HTTP calls?

**Without Kafka (tight coupling):**
```
auth-service → HTTP POST → notification-service (send OTP email)
```
If notification-service is down → signup fails. auth-service waits for email to send.

**With Kafka (loose coupling):**
```
auth-service → publishes to Kafka → returns 201 to client immediately
                                          ↓
                          notification-service picks up event (whenever it's ready)
                          and sends email in the background
```
If notification-service restarts, Kafka replays any missed events automatically.

### Kafka topics in Blinkit

| Topic | Published by | Consumed by | Triggered by |
|---|---|---|---|
| `user.registered` | auth-service | notification-service, user-service | New signup |
| `user.password.reset` | auth-service | notification-service | Forgot password |
| `product.created` | product-service | inventory-service | Admin creates product |
| `inventory.low` | inventory-service | notification-service | Stock < threshold |
| `inventory.out` | inventory-service | product-service | Stock = 0 |
| `connectivity.test` | infra-check.sh | infra-check.sh | Health check script |
| `order.created` | order-service | inventory-service, notification-service | Customer places order |
| `payment.done` | payment-service | order-service, notification-service | Payment confirmed |
| `payment.failed` | payment-service | order-service, notification-service | Payment failed |
| `order.status.updated` | order-service | notification-service | Order dispatched/delivered |

### Key Kafka concepts

| Concept | Meaning |
|---|---|
| **Topic** | Named channel — like a category for events (e.g. `order.created`) |
| **Producer** | Service that publishes events |
| **Consumer** | Service that reads events |
| **Consumer group** | Multiple instances of a service share load — each event processed once per group |
| **Offset** | Kafka tracks which messages each consumer has read — restarts replay missed events |

### Kafka connection config
```
Bootstrap servers: localhost:9092 (or KAFKA_BOOTSTRAP_SERVERS from .env)
```

---

## 5. Zookeeper

### What is Zookeeper?
Kafka's internal coordination service. It manages:
- Which Kafka broker is the leader
- Consumer group offsets and metadata
- Cluster membership

### Do you interact with it directly?
**Never.** Zookeeper runs silently. You never call it or configure it for app logic. It simply must be running before Kafka starts — `docker-compose.infra.yml` starts it first with a healthcheck, and Kafka has `depends_on: zookeeper`.

### NodeExistsException (common issue after PC restart)
If Kafka fails to start with `NodeExistsException` in its logs, it means Zookeeper has stale metadata from the previous run that conflicts with Kafka trying to re-register. Fix: wipe the volumes with `down -v`.

`start-infra.sh` handles this automatically — it detects if Kafka exited immediately after starting and triggers a clean restart.

---

## 6. Kafka UI & Redis Commander

### Kafka UI — `http://localhost:9093`
Browser dashboard for Kafka (Provectus).

Use it to:
- Verify a topic was created (e.g. `user.registered` after first signup)
- Browse messages inside a topic to debug event payloads
- Check consumer group lag — how far behind a consumer is
- Confirm events are being published and consumed correctly

### Redis Commander — `http://localhost:9191`
Browser UI to inspect all Redis keys, values, and TTLs.

Use it to:
- Check if a refresh token is stored for a user
- Verify OTP key exists and see its remaining TTL
- Inspect cart contents
- Confirm blacklist entries exist after logout

---

## 7. Shell Scripts

All scripts live in `backend/`. Run them from the `backend/` directory.

### `start-infra.sh`
Ensures Colima and all Docker infra containers are up and healthy. Called automatically by `start-backend.sh` — you rarely need to run it directly.

**What it does (in order):**
1. Checks `docker info` → if Docker is unreachable, runs `colima start` and waits up to 40s for the Docker socket
2. Checks if `blinkit-redis` and `blinkit-kafka` containers are running → if not, runs `docker compose up -d`
3. Detects if Kafka exited immediately (NodeExistsException) → auto-runs `down -v` + `up -d` for a clean restart
4. Waits for Redis to be `healthy` via `docker inspect` (up to 40s)
5. Waits for Kafka to be `healthy` via `docker inspect` (up to 60s)

### `start-backend.sh`
Starts everything — infra + all Spring Boot services in the correct order.

**Startup order:**
```
start-infra.sh (Colima + Docker containers)
    ↓
infra-check.sh (verifies MongoDB + Redis + Kafka connectivity — 15 tests)
    ↓
Eureka Server       (8761) — service registry
    ↓
Config Server       (8888) — central config
    ↓
API Gateway         (8080)
    ↓
Auth Service        (8081)
    ↓
User Service        (8082)
    ↓
Notification Svc    (8089)
    ↓
Product Service     (8083)
    ↓
Inventory Service   (8084)
```
Each service waits for its port to open before the next one starts.

### `stop-infra.sh`
Full shutdown — stops everything.

**What it does (in order):**
1. Kills all Spring Boot services by port (8761–8091) + PID file
2. Stops Docker containers + wipes volumes (`down -v`) — clean state for next start
3. Stops Colima

### `stop-backend.sh`
Stops only Spring Boot services (leaves Docker containers and Colima running).

### `infra-check.sh`
Runs 15 connectivity tests against MongoDB, Redis, and Kafka. Called by `start-backend.sh` after infra is up.

| Section | Tests |
|---|---|
| MongoDB Atlas | INSERT → READ → COUNT verify → DELETE → DROP test db |
| Redis | PING → SET → GET/verify → TTL verify → DELETE → EXISTS verify |
| Kafka | Container check → create test topic → produce → consume/verify |

---

## 8. How to Start Everything

### The only command you need (after any restart)
```bash
cd backend
./start-backend.sh
```

This single command:
- Starts Colima if not running
- Starts Docker infra containers if not running
- Handles Kafka NodeExistsException automatically (clean restart)
- Waits for Redis + Kafka to be healthy
- Runs infra connectivity checks (15 tests)
- Starts all 8 Spring Boot services in the correct order

### Manual infra-only start (if needed)
```bash
cd backend
./start-infra.sh
```

### Manual docker compose only (bypasses the script)
```bash
# Must have Colima running first
colima start

# Start containers
docker compose -f docker-compose.infra.yml up -d

# Check container status
docker ps
```

---

## 9. How to Stop Everything

### Full shutdown (services + containers + Colima)
```bash
cd backend
./stop-infra.sh
```

This stops:
1. All Spring Boot services (by port + PID file)
2. All Docker containers + wipes volumes (`down -v`)
3. Colima

Volumes are wiped on every stop so the next `./start-backend.sh` always starts from a clean state (avoids Kafka NodeExistsException).

### Stop only Spring Boot services (keep Docker running)
```bash
cd backend
./stop-backend.sh
```

### Manual docker compose stop
```bash
# Stop containers, keep volumes (data preserved)
docker compose -f docker-compose.infra.yml down

# Stop containers + wipe volumes (clean state)
docker compose -f docker-compose.infra.yml down -v
```

---

## 10. Troubleshooting

### Kafka fails to start — `NodeExistsException`
**Cause:** Stale Zookeeper metadata from a previous run.
**Auto-fix:** `start-infra.sh` detects this and does a clean restart automatically.
**Manual fix:**
```bash
docker compose -f docker-compose.infra.yml down -v
docker compose -f docker-compose.infra.yml up -d
```

### Redis connectivity check fails
```bash
# Check if container is running
docker ps | grep blinkit-redis

# Check container logs
docker logs blinkit-redis

# Test manually
redis-cli -a localpassword ping   # should return PONG
```

### Colima not starting
```bash
# Check status
colima status

# Force restart
colima stop
colima start

# Nuclear option (delete VM, recreates from scratch)
colima delete
colima start
```

### Spring Boot service fails to start
```bash
# Check the log for that service
tail -100 backend/logs/auth-service.log
tail -100 backend/logs/api-gateway.log
```

### Check which ports are in use
```bash
lsof -i :8761    # Eureka
lsof -i :8888    # Config Server
lsof -i :8080    # API Gateway
lsof -i :8081    # Auth Service
lsof -i :6379    # Redis
lsof -i :9092    # Kafka
```

---

## 11. Quick Reference URLs

| Service | URL | Purpose |
|---|---|---|
| Eureka Dashboard | http://localhost:8761 | See all registered microservices |
| API Gateway Health | http://localhost:8080/actuator/health | Gateway status |
| Auth Service Health | http://localhost:8081/actuator/health | Auth status |
| User Service Health | http://localhost:8082/actuator/health | User status |
| Product Service Health | http://localhost:8083/actuator/health | Product status |
| Inventory Service Health | http://localhost:8084/actuator/health | Inventory status |
| Kafka UI | http://localhost:9093 | Browse Kafka topics + messages |
| Redis Commander | http://localhost:9191 | Browse Redis keys + TTLs |