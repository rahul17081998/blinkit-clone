# Blinkit Clone — Infrastructure Guide

> Covers every service running in `docker-compose.infra.yml` — what it is, why it exists, and how it fits into the system.

---

## What's Running

| Service | Port | Technology |
|---|---|---|
| Redis | 6379 | In-memory key-value store |
| Redis Commander | 9191 | Redis browser UI |
| Kafka | 9092 | Distributed event streaming |
| Kafka UI | 9093 | Kafka browser UI |
| Zookeeper | 2181 | Kafka coordinator (internal) |

---

## Redis `localhost:6379`

### What is it?
An in-memory key-value store. Reads and writes happen in **under 1ms** because everything lives in RAM, not on disk.

### Why not just use MongoDB for everything?
MongoDB is a great persistent database but it is disk-based and designed for complex queries. Redis is purpose-built for three things MongoDB is bad at:
- **Speed** — sub-millisecond access for hot data
- **TTL (auto-expiry)** — keys delete themselves after a set time, no cron job needed
- **Atomic counters** — perfect for rate limiting

### How Blinkit uses Redis

| Purpose | Key Pattern | TTL | Stage |
|---|---|---|---|
| Rate limiting (per user/IP) | managed by Spring Gateway internally | per window | Stage 1 |
| JWT refresh tokens | `refresh:{userId}` | 30 days | Stage 2 |
| JWT blacklist (on logout) | `blacklist:{token}` | until token expiry | Stage 2 |
| OTP codes (signup verification) | `otp:{email}` | 5 minutes | Stage 2 |
| Password reset tokens | `pwd_reset:{token}` | 15 minutes | Stage 2 |
| User cart | `cart:{userId}` | no expiry | Stage 4 |

### Real example — OTP flow
1. User signs up → auth-service generates a 6-digit OTP
2. OTP stored in Redis: `SET otp:user@email.com 482910 EX 300` (expires in 5 min)
3. Notification-service sends the OTP via email
4. User submits OTP → auth-service calls `GET otp:user@email.com`
5. Match → mark user verified, `DEL otp:user@email.com`
6. No match or key expired → return 400 Bad Request

No database row created, no cron job to clean up — Redis handles expiry automatically.

### Real example — Rate limiting
The API Gateway uses Redis token-bucket algorithm per user:
- Each user gets **20 requests/second**, burst up to **40**
- Gateway stores the bucket state in Redis under the user's ID
- If bucket is empty → 429 Too Many Requests

---

## Redis Commander `localhost:9191`

### What is it?
A browser-based UI to inspect Redis keys, values, and TTLs visually.

### When to use it
- Debugging auth flows — check if a refresh token is actually stored
- Verify OTP key exists and its remaining TTL
- Inspect cart contents during Stage 4 development
- Confirm token blacklist entries after logout

### How to open
`http://localhost:9191`

---

## Apache Kafka `localhost:9092`

### What is it?
A distributed event streaming platform. Services publish **events** (messages) to **topics**, and other services subscribe to consume them — without knowing who published them or who is listening.

### Why Kafka instead of direct service calls?

**Without Kafka (tight coupling):**
```
auth-service  →  HTTP call  →  notification-service  (send OTP email)
```
If notification-service is down, the signup fails. auth-service must wait for the email to send before responding to the user.

**With Kafka (loose coupling):**
```
auth-service  →  publishes event to Kafka  →  returns 200 to user immediately
                                           ↓
                              notification-service picks up event
                              and sends email in the background
```
auth-service doesn't know or care about notification-service. If notification-service restarts, Kafka replays the missed events automatically.

### Kafka topics used in Blinkit

| Topic | Published by | Consumed by | What triggers it |
|---|---|---|---|
| `user.registered` | auth-service | notification-service | New user signup |
| `user.password.reset` | auth-service | notification-service | Forgot password request |
| `order.created` | order-service | inventory-service, notification-service | Customer places an order |
| `payment.done` | payment-service | order-service, notification-service | Razorpay payment confirmed |
| `payment.failed` | payment-service | order-service, notification-service | Payment failed |
| `inventory.low` | inventory-service | notification-service | Stock falls below threshold |
| `order.status.updated` | order-service | notification-service | Order dispatched / delivered |

### Real example — Order placed flow
```
Customer clicks "Place Order"
        ↓
order-service creates order in MongoDB (status: PENDING)
        ↓
order-service publishes → topic: order.created
        ↓
    ┌───┴──────────────────────────────────────────┐
    ▼                                              ▼
inventory-service                      notification-service
deducts stock                          sends "Order Confirmed" email + SMS
        ↓
publishes → topic: inventory.low  (if stock < threshold)
        ↓
notification-service
sends "Low stock alert" email to admin
```
All of this happens **asynchronously** after the customer already got their 200 response.

### Key Kafka concepts

| Concept | Meaning |
|---|---|
| **Topic** | A named channel — like a queue category (e.g. `order.created`) |
| **Producer** | Service that publishes events to a topic |
| **Consumer** | Service that reads events from a topic |
| **Consumer group** | Multiple instances of a service share the load — each event is processed once |
| **Offset** | Kafka tracks which messages each consumer has read — missed messages are replayed on restart |

---

## Zookeeper `localhost:2181`

### What is it?
Kafka's internal coordination service. It manages:
- Which Kafka broker is the leader
- Consumer group offsets and metadata
- Cluster membership

### Do you ever interact with it directly?
**No.** Zookeeper runs silently in the background. You never call it, configure it for app logic, or open its port in a browser. It simply must be running before Kafka starts — that's why `docker-compose.infra.yml` starts Zookeeper first with a healthcheck, and Kafka has `depends_on: zookeeper`.

---

## Kafka UI `localhost:9093`

### What is it?
A browser-based dashboard for Kafka (Provectus Kafka UI).

### When to use it
- Verify that a topic was created (e.g. `user.registered` appears after first signup)
- Browse messages inside a topic to debug event payloads
- Check consumer group lag — how many messages a consumer is behind
- Confirm events are being published and consumed correctly

### How to open
`http://localhost:9093`

---

## Why not use MongoDB for all of this?

| Requirement | MongoDB | Redis / Kafka |
|---|---|---|
| Sub-millisecond reads | Too slow (disk I/O) | Redis: in-memory, <1ms |
| Auto-expiring keys (OTP, tokens) | Needs TTL index + background job | Redis: native TTL, zero config |
| Rate limiting (per-request) | Cannot do it at this speed | Redis: atomic counters, instant |
| Async event delivery | Not designed for it | Kafka: built for exactly this |
| Replay missed messages on restart | No | Kafka: offset-based replay |
| Decoupled services | Requires direct HTTP calls | Kafka: publish once, many consumers |

MongoDB is still the **primary database** for all persistent business data (users, products, orders, etc.). Redis and Kafka handle the **operational layer** — speed, ephemeral state, and async communication.

---

## Start / Stop Infrastructure

```bash
# Start all infra containers
docker compose -f docker-compose.infra.yml up -d

# Check status
docker ps

# Stop all infra containers
docker compose -f docker-compose.infra.yml down

# Stop and delete all volumes (wipe Redis + Kafka data)
docker compose -f docker-compose.infra.yml down -v
```

## Quick Access URLs

| Service | URL | Use |
|---|---|---|
| Eureka Dashboard | http://localhost:8761 | See registered microservices |
| API Gateway Health | http://localhost:8080/actuator/health | Gateway status |
| Kafka UI | http://localhost:9093 | Browse topics and messages |
| Redis Commander | http://localhost:9191 | Browse Redis keys |
