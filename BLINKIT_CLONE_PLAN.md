# Blinkit Clone — Full System Design & Implementation Plan

> **Stack:** React 18 (Vite + React Router) + Spring Boot 3 + MongoDB Atlas + Redis + Kafka + JWT
> **Architecture:** Microservices (Spring Cloud)
> **Reference:** Based on journalApp JWT implementation pattern

---

## TABLE OF CONTENTS

1. [System Architecture Overview](#1-system-architecture)
2. [Microservices Breakdown](#2-microservices-breakdown)
3. [Database Design — Collections & Indexes](#3-database-design)
   - 3.1 [Entity Schemas — All Fields (18 collections)](#31-entity-schemas--all-fields)
4. [Kafka Event Topics](#4-kafka-events)
5. [Redis Usage Plan](#5-redis-usage)
6. [Inter-Service Communication (Feign Clients)](#6-inter-service-communication-feign-clients)
7. [API Design (Request/Response Format, Swagger)](#7-api-design)
8. [UI/UX Design Plan](#8-uiux-design)
9. [Admin Panel Design](#9-admin-panel-design)
10. [Authentication & Authorization Flow](#10-auth-flow)
11. [Development Roadmap](#11-development-roadmap)
12. [Docker — Containerisation & Deployment](#12-docker--containerisation--deployment)
    - 12.1 Folder Structure
    - 12.2 Dockerfile — Spring Boot (multi-stage)
    - 12.3 Dockerfile — React + Nginx (multi-stage)
    - 12.4 .env — All environment variables
    - 12.5 docker-compose.yml — Full local stack
    - 12.6 Service startup order
    - 12.7 docker-compose.infra.yml — Dev-only infra
    - 12.8 Useful Docker commands
    - 12.9 docker-compose.prod.yml — Production overrides
    - 12.10 .dockerignore
    - 12.11 Inter-container communication

---

## 1. SYSTEM ARCHITECTURE

```
                          ┌─────────────────────────────────────────┐
                          │           CLIENT APPLICATIONS            │
                          │  React Web App   │ Admin Panel (React)  │
                          └────────────────────┬────────────────────┘
                                               │ HTTPS
                          ┌────────────────────▼────────────────────┐
                          │            API GATEWAY                   │
                          │   (Spring Cloud Gateway - Port 8080)     │
                          │  • Route requests to services            │
                          │  • JWT validation (global filter)        │
                          │  • Rate limiting (Redis)                 │
                          │  • CORS handling                         │
                          └──┬──────┬──────┬──────┬──────┬──────┬──┘
                             │      │      │      │      │      │
              ┌──────────────▼─┐ ┌──▼──┐ ┌▼────┐ │  ┌───▼──┐ ┌▼──────────┐
              │ Auth Service   │ │User │ │Prod.│ │  │Order │ │Payment    │
              │ :8081          │ │Svc  │ │Svc  │ │  │Svc   │ │Service    │
              │• Login/Signup  │ │:8082│ │:8083│ │  │:8085 │ │:8086      │
              │• JWT tokens    │ │     │ │     │ │  │      │ │           │
              └───────┬────────┘ └──┬──┘ └──┬──┘ │  └───┬──┘ └──┬────────┘
                      │             │       │    │      │        │
                      │    ┌────────▼──┐ ┌──▼────▼──┐ ┌▼────────▼─┐
                      │    │Cart Svc   │ │Inventory  │ │Delivery   │
                      │    │:8087      │ │Svc :8084  │ │Svc :8088  │
                      │    │(Redis)    │ │           │ │           │
                      │    └───────────┘ └───────────┘ └───────────┘
                      │
              ┌───────▼──────────┐        ┌─────────────────────┐
              │Notification Svc  │◄───────│   KAFKA BROKER       │
              │:8089             │        │ (Event Bus)          │
              │• Email (SMTP)    │        │ • order.created      │
              │• SMS (Twilio)    │        │ • payment.done       │
              │• Push notif.     │        │ • inventory.low      │
              └──────────────────┘        └─────────────────────┘

              ┌─────────────────────────────────────────────────┐
              │                  DATA LAYER                      │
              │  MongoDB Atlas    Redis Cache    MongoDB Atlas   │
              │  (Primary DB)     (Cart/Tokens)  (Analytics DB) │
              └─────────────────────────────────────────────────┘

              ┌─────────────────────────────────────────────────┐
              │         ADDITIONAL DOMAIN SERVICES               │
              │  Coupon Service  :8090  (Discount coupons)       │
              │  Review Service  :8091  (Product ratings)        │
              └─────────────────────────────────────────────────┘

              ┌─────────────────────────────────────────────────┐
              │           INFRASTRUCTURE / PLATFORM              │
              │  Eureka Server (Service Discovery) :8761         │
              │  Spring Cloud Config Server :8888                │
              │  Cloudinary (Product Image Storage)              │
              └─────────────────────────────────────────────────┘

              ┌─────────────────────────────────────────────────┐
              │            AUDIT / OBSERVABILITY                 │
              │  Audit Service  :8092                            │
              │  • Kafka spy consumer (all topics passively)     │
              │  • audit.events consumer (explicit audit records)│
              │  • Correlation ID trace across all services      │
              │  • MongoDB: audit_db.event_logs                  │
              └─────────────────────────────────────────────────┘
```

---

## 2. MICROSERVICES BREAKDOWN

### 2.1 API Gateway — `api-gateway` (Port 8080)

**Technology:** Spring Cloud Gateway

**Responsibilities:**
- Route all incoming requests to the appropriate microservice
- Global JWT authentication filter (validate token, attach user context headers)
- Rate limiting per user (Redis token bucket)
- Circuit breaker (Resilience4j)
- Request/response logging

**Routes:**
```
/api/auth/**       → auth-service:8081
/api/users/**      → user-service:8082
/api/products/**   → product-service:8083
/api/inventory/**  → inventory-service:8084
/api/orders/**     → order-service:8085
/api/payments/**   → payment-service:8086
/api/cart/**       → cart-service:8087
/api/delivery/**   → delivery-service:8088
/api/coupons/**    → coupon-service:8090
/api/reviews/**    → review-service:8091
/api/admin/**      → (routes to relevant services with ADMIN role check)
```

**Public routes (no JWT required):**
```
/api/auth/login, /api/auth/signup, /api/auth/refresh
/api/auth/forgot-password, /api/auth/reset-password
/api/auth/verify
/api/products (GET), /api/categories (GET)
```

**Config:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=1
```

---

### 2.2 Auth Service — `auth-service` (Port 8081)

**Reuse pattern from:** `journalApp/filter/JwtFilter.java` + `JwtUtil.java` + `SpringSecurity.java`

**MongoDB Collection:** `auth_db.users`

**Responsibilities:**
- User registration (customer + admin)
- Login → returns JWT access token + refresh token
- Token refresh endpoint
- OTP-based email verification (same as journalApp)
- Password reset via **secure email link** (UUID token stored in Redis with 15 min TTL)
- Logout (invalidate refresh token in Redis)
- Role management: CUSTOMER, ADMIN, DELIVERY_PARTNER

**Key Classes:**
```
auth-service/
├── config/
│   ├── SpringSecurity.java         ← from journalApp pattern
│   └── JwtConfig.java
├── filter/
│   └── JwtFilter.java              ← from journalApp pattern
├── utils/
│   └── JwtUtil.java                ← from journalApp pattern
├── controller/
│   ├── AuthController.java         → /auth/login, /auth/signup, /auth/refresh
│   └── PasswordController.java     → /auth/forgot-password, /auth/reset-password/{token}
├── entity/
│   └── AuthUser.java               ← username, password, roles, verified, refreshToken
├── service/
│   ├── AuthService.java
│   ├── OtpService.java             ← email OTP for signup verification only
│   └── PasswordResetService.java   ← generates UUID token, stores in Redis, sends link via email
```

**JWT Token Claims:**
```json
{
  "sub": "user@email.com",
  "userId": "mongo_object_id",
  "role": "CUSTOMER",
  "email": "user@email.com",
  "iat": 1700000000,
  "exp": 1700003600
}
```

**Endpoints:**
```
POST /auth/signup                           → Register new customer
POST /auth/login                            → Login, returns {accessToken, refreshToken, user}
POST /auth/refresh                          → Refresh access token
POST /auth/logout                           → Invalidate refresh token
GET  /auth/verify?otp=123456               → Email OTP verification (signup only)
POST /auth/forgot-password                  → Send password reset LINK to email
GET  /auth/reset-password/validate/{token}  → Validate reset token (frontend calls to check if token valid/expired)
POST /auth/reset-password/{token}           → Set new password using token from email link
```

**Password Reset via Email Link Flow:**
```
1. User POST /auth/forgot-password {email}
2. auth-service generates UUID token (e.g. "a1b2c3d4-...")
3. Stores in Redis: pwd_reset:{token} = userId  (TTL: 15 min)
4. Sends email: "Reset your password → https://app.com/reset-password/a1b2c3d4..."
5. User clicks link → browser opens /reset-password/a1b2c3d4
6. Frontend calls GET /auth/reset-password/validate/a1b2c3d4
   → 200 OK (token valid) or 410 GONE (expired/invalid)
7. User enters new password
8. Frontend calls POST /auth/reset-password/a1b2c3d4 {newPassword}
9. auth-service validates token from Redis, updates password, deletes token from Redis
10. Returns success → redirect to login
```

---

### 2.3 User Service — `user-service` (Port 8082)

**MongoDB Collection:** `user_db.user_profiles`

**Responsibilities:**
- Customer profile management (name, phone, profile photo)
- Delivery address CRUD (add, update, delete, set default)
- Order history view (calls order-service via Feign)
- Account deletion

**Endpoints:**
```
GET    /users/profile              → Get my profile
PUT    /users/profile              → Update profile
POST   /users/addresses            → Add delivery address
GET    /users/addresses            → List all addresses
PUT    /users/addresses/{id}       → Update address
DELETE /users/addresses/{id}       → Delete address
PUT    /users/addresses/{id}/default → Set as default
```

**Address Schema:**
```json
{
  "id": "addr_id",
  "label": "Home",
  "flatNo": "B-204",
  "building": "Green Valley",
  "area": "Koramangala",
  "city": "Bangalore",
  "pincode": "560034",
  "landmark": "Near Cafe Coffee Day",
  "lat": 12.9352,
  "lng": 77.6245,
  "isDefault": true
}
```

---

### 2.4 Product Service — `product-service` (Port 8083)

**MongoDB Collections:** `product_db.products`, `product_db.categories`

**Responsibilities:**
- Product catalog management
- Category hierarchy (Fruits & Veggies > Apples)
- Product search with text index (MongoDB full-text search)
- Image upload (store URL, use S3/Cloudinary)
- Admin: Create/update/delete products, set pricing

**Endpoints:**
```
GET  /products                     → List products (filter, sort, paginate)
GET  /products/{id}                → Product detail
GET  /products/search?q=apple      → Search products
GET  /products/category/{slug}     → Products by category
GET  /categories                   → Category tree

# Admin only:
POST   /admin/products             → Create product
PUT    /admin/products/{id}        → Update product
DELETE /admin/products/{id}        → Delete product
PUT    /admin/products/{id}/toggle → Toggle availability
POST   /admin/categories           → Create category
```

**Product Document:**
```json
{
  "_id": "prod_id",
  "name": "Amul Full Cream Milk 500ml",
  "slug": "amul-full-cream-milk-500ml",
  "description": "Fresh full cream milk",
  "images": ["url1", "url2"],
  "category": { "id": "cat_id", "name": "Dairy", "slug": "dairy" },
  "brand": "Amul",
  "mrp": 30.00,
  "sellingPrice": 28.00,
  "discount": 7,
  "unit": "500ml",
  "tags": ["milk", "dairy", "amul"],
  "isAvailable": true,
  "isFeatured": false,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

**Redis Caching:** Cache top products, categories for 10 min TTL

---

### 2.5 Inventory Service — `inventory-service` (Port 8084)

**MongoDB Collection:** `inventory_db.stock`

**Responsibilities:**
- Track stock quantity per product
- Reserve stock on order creation (optimistic locking)
- Release reserved stock on order cancellation
- Low-stock alerts via Kafka → notification service
- Admin: View stock levels, update stock

**Endpoints:**
```
GET  /inventory/{productId}         → Get stock for product
POST /inventory/reserve             → Reserve stock (called by order-service)
POST /inventory/release             → Release reserved stock
POST /inventory/confirm             → Confirm stock deduction after payment

# Admin:
GET  /admin/inventory               → Full stock report
PUT  /admin/inventory/{productId}   → Update stock quantity
```

**Kafka Events Published:**
- `inventory.low` — when stock < threshold
- `inventory.out` — when stock = 0

---

### 2.6 Order Service — `order-service` (Port 8085)

**MongoDB Collection:** `order_db.orders`

**Responsibilities:**
- Create orders from cart
- Order lifecycle: PENDING → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
- Order cancellation (before OUT_FOR_DELIVERY)
- Order history per user
- Coordinates with inventory-service, payment-service, delivery-service

**Order States:**
```
PENDING → PAYMENT_PENDING → CONFIRMED → PREPARING → PACKED
       → OUT_FOR_DELIVERY → DELIVERED
       → CANCELLED
       → PAYMENT_FAILED
```

**Endpoints:**
```
POST /orders                   → Create order from cart
GET  /orders                   → My orders list
GET  /orders/{id}              → Order detail + tracking
POST /orders/{id}/cancel       → Cancel order

# Admin:
GET  /admin/orders             → All orders
PUT  /admin/orders/{id}/status → Update order status
```

**Kafka Events Published:**
- `order.created` → payment-service
- `order.confirmed` → inventory-service (confirm deduction)
- `order.cancelled` → inventory-service (release stock), payment-service (refund)
- `order.status.updated` → notification-service

---

### 2.7 Payment Service — `payment-service` (Port 8086)

**MongoDB Collection:** `payment_db.transactions`

**Responsibilities:**
- Payment initiation (integrate Razorpay / Stripe)
- Payment verification via webhook
- Refund processing
- Transaction history

**Payment Flow:**
```
1. Frontend calls POST /payments/initiate
2. Payment service creates order with Razorpay
3. Returns {orderId, amount, razorpayOrderId, key}
4. Frontend opens Razorpay checkout UI
5. User pays → Razorpay calls webhook
6. Payment service verifies signature
7. Publishes payment.success / payment.failed to Kafka
8. Order service updates order status
```

**Endpoints:**
```
POST /payments/initiate            → Create payment session
POST /payments/verify              → Verify payment (called after frontend payment)
POST /payments/webhook             → Razorpay webhook handler
GET  /payments/history             → Transaction history
POST /payments/refund/{txnId}      → Initiate refund (admin/cancellation)
```

---

### 2.8 Cart Service — `cart-service` (Port 8087)

**Storage:** Redis only (fast, ephemeral)

**Responsibilities:**
- Add/remove/update items in cart
- Calculate totals (calls product-service for price)
- Apply promo codes
- Cart persists for 7 days (Redis TTL)
- Cart is per user (key: `cart:{userId}`)

**Redis Key Structure:**
```
cart:{userId}  →  Hash {
  "prod_id_1": {"quantity": 2, "price": 28.0, "name": "Amul Milk"},
  "prod_id_2": {"quantity": 1, "price": 150.0, "name": "Bread"}
}
cart:{userId}:promo  → "SAVE10"
```

**Endpoints:**
```
GET    /cart                   → Get my cart with totals
POST   /cart/items             → Add item {productId, quantity}
PUT    /cart/items/{productId} → Update quantity
DELETE /cart/items/{productId} → Remove item
DELETE /cart                   → Clear cart
POST   /cart/promo             → Apply promo code
DELETE /cart/promo             → Remove promo code
```

---

### 2.9 Delivery Service — `delivery-service` (Port 8088)

**MongoDB Collection:** `delivery_db.delivery_tasks`

**Responsibilities:**
- Assign delivery partner to order
- Real-time location tracking (WebSocket)
- Delivery slot management
- Estimated delivery time calculation

**Endpoints:**
```
GET  /delivery/slots                → Available delivery slots
GET  /delivery/track/{orderId}      → Real-time tracking info
POST /delivery/assign               → Assign delivery partner (admin)

# Delivery partner:
PUT  /delivery/{taskId}/status      → Update delivery status
PUT  /delivery/{taskId}/location    → Update location (lat, lng)
```

---

### 2.10 Coupon Service — `coupon-service` (Port 8090)

**MongoDB Collection:** `coupon_db.coupons`

**Responsibilities:**
- Create/manage discount coupons (admin)
- Validate coupon on cart apply (check eligibility, usage limit, expiry)
- Track coupon usage per user (prevent re-use)

**Coupon Types:**
```
FLAT        → Fixed ₹ discount (e.g. ₹50 off)
PERCENT     → Percentage discount (e.g. 10% off, max ₹100)
FIRST_ORDER → Only for first order per user
MIN_ORDER   → Applicable only if cart total > X
```

**Endpoints:**
```
POST /coupons/validate          → Validate coupon {code, userId, cartTotal}
                                  Returns: {valid, discount, message}

# Admin only:
POST   /admin/coupons           → Create coupon
GET    /admin/coupons           → List all coupons
PUT    /admin/coupons/{id}      → Update coupon
DELETE /admin/coupons/{id}      → Delete coupon
GET    /admin/coupons/{id}/usage → View usage stats
```

**Coupon Document:**
```json
{
  "_id": "coup_id",
  "code": "SAVE10",
  "type": "PERCENT",
  "value": 10,
  "maxDiscount": 100,
  "minOrderAmount": 149,
  "usageLimit": 1000,
  "usedCount": 342,
  "perUserLimit": 1,
  "validFrom": "2024-01-01T00:00:00Z",
  "validUntil": "2024-12-31T00:00:00Z",
  "isActive": true
}
```

---

### 2.11 Review Service — `review-service` (Port 8091)

**MongoDB Collection:** `review_db.reviews`

**Responsibilities:**
- Allow customers to rate & review a product after delivery
- One review per user per product per order
- Admin can moderate reviews (delete/flag)
- Product service reads avg rating (via Feign)

**Endpoints:**
```
POST /reviews                  → Submit review {productId, orderId, rating, comment}
GET  /reviews/product/{id}     → Get reviews for a product (paginated)
GET  /reviews/my               → My reviews

# Admin:
DELETE /admin/reviews/{id}     → Delete review
```

**Review Document:**
```json
{
  "_id": "rev_id",
  "productId": "prod_id",
  "orderId": "order_id",
  "userId": "user_id",
  "rating": 4,
  "comment": "Fresh and good quality",
  "createdAt": "2024-01-05T10:00:00Z"
}
```

---

### 2.12 Notification Service — `notification-service` (Port 8089)

**Kafka Consumer**

**Responsibilities:**
- Consume Kafka events and send notifications
- Email via SMTP (same as journalApp)
- SMS via Twilio (same as journalApp)
- Push notifications (Firebase FCM)

**Topic → Action Mapping:**
```
user.registered        → Email: Welcome email + OTP for verification
order.created          → Email: "Order #123 received!"
order.confirmed        → Email + SMS: "Your order is confirmed"
order.out_for_delivery → SMS + Push: "Your order is on the way!"
order.delivered        → Email + Push: "Delivered! Rate your experience" (with review link)
order.cancelled        → Email + SMS: "Order cancelled, refund initiated"
payment.success        → Email: Payment receipt with invoice
payment.failed         → Email + SMS: "Payment failed — retry here"
inventory.low          → Email (admin only): "Low stock alert for {product}"
user.password.reset    → Email: Password reset link (15 min expiry)
```

---

### 2.13 Audit Service — `audit-service` (Port 8092)

**MongoDB Database:** `audit_db`

**Purpose:**
A dedicated observability service that records every significant inter-service message flow in the system — Kafka events, notification delivery outcomes, payment gateway interactions, and Feign call results. Acts as a full audit trail: what was sent, on which topic, what the payload was, which service processed it, and whether it succeeded or failed.

---

**Two capture mechanisms:**

**1. Kafka Spy Consumer (passive — captures all Kafka traffic)**
The audit service subscribes to every Kafka topic under a dedicated consumer group `audit-consumer-group`. This group is completely independent — it does not interfere with other consumer groups. Every Kafka message published in the system is automatically captured by the audit service with full metadata.

**2. `audit.events` Topic (active — captures non-Kafka events)**
For events that don't travel over Kafka (notification delivery outcomes, Feign call results, payment webhook receipts), the relevant service publishes a structured audit record to the `audit.events` topic. The audit service consumes this topic and persists the record.

```
notification-service  → publishes to audit.events after SMTP/SMS/FCM attempt (status: SENT / FAILED)
payment-service       → publishes to audit.events on Razorpay webhook receipt + signature verification result
order-service         → publishes to audit.events on Feign call to inventory-service (reserve/release result)
cart-service          → publishes to audit.events on Feign call to product-service and coupon-service
```

---

**Responsibilities:**
- Record every Kafka message: topic, partition, offset, key, headers, payload, timestamp
- Record which consumer group processed each message and the processing outcome
- Record notification delivery outcomes (SMTP/Twilio/FCM) — status, error, retry count
- Record payment gateway events — Razorpay webhook payload, HMAC verification result, response
- Record Feign inter-service calls — request payload, response payload, HTTP status, duration
- Track correlation IDs across service boundaries to reconstruct end-to-end request flows
- Expose admin query API to search, filter, and trace events

---

**AuditEventLog Document:**
```json
{
  "_id": "ObjectId",
  "auditId": "uuid-v4",
  "correlationId": "uuid-v4",

  "eventCategory": "KAFKA | NOTIFICATION | PAYMENT_GATEWAY | FEIGN_CALL | WEBHOOK",
  "eventType": "order.created | EMAIL_SENT | PAYMENT_WEBHOOK | FEIGN_RESERVE_STOCK | ...",

  "sourceService": "order-service",
  "sourceAction": "POST /orders",

  "targetService": "notification-service",
  "targetAction": "KAFKA_CONSUME | SMTP_SEND | FEIGN_CALL | WEBHOOK_HANDLER",

  "kafkaTopic": "order.created",
  "kafkaPartition": 0,
  "kafkaOffset": 142,
  "kafkaConsumerGroup": "notification-group",
  "kafkaKey": "order_id_xyz",
  "kafkaHeaders": { "X-Correlation-Id": "uuid", "X-User-Id": "uid" },

  "requestPayload": { "orderId": "...", "userId": "...", "totalAmount": 256.50 },
  "responsePayload": { "reserved": true },
  "responseTopic": "payment.success",

  "status": "SUCCESS | FAILED | PENDING | RETRY",
  "errorCode": null,
  "errorMessage": null,
  "processingTimeMs": 42,
  "retryCount": 0,

  "publishedAt": "2024-01-01T10:00:00Z",
  "consumedAt": "2024-01-01T10:00:00.042Z",
  "processedAt": "2024-01-01T10:00:00.084Z",

  "userId": "user_id",
  "entityId": "order_id",
  "entityType": "ORDER | PAYMENT | USER | NOTIFICATION | INVENTORY",
  "createdAt": "2024-01-01T10:00:00Z"
}
```

---

**Correlation ID Flow:**
Every incoming request to the API Gateway generates a `X-Correlation-Id` UUID header. The gateway injects this into all downstream requests. Services forward it in Kafka message headers. The audit service uses it to link all audit records belonging to the same end-to-end flow.

```
Customer → POST /orders
  └─ Gateway injects X-Correlation-Id: abc-123
       └─ order-service creates order           [auditId: 1, correlationId: abc-123]
            └─ Feign → inventory-service        [auditId: 2, correlationId: abc-123]
            └─ Kafka → order.created            [auditId: 3, correlationId: abc-123]
                 └─ notification-service EMAIL  [auditId: 4, correlationId: abc-123]
                 └─ payment-service processes   [auditId: 5, correlationId: abc-123]
                      └─ Kafka → payment.success [auditId: 6, correlationId: abc-123]
```

One `correlationId` → full trace of all 6 audit records for a single order placement.

---

**Endpoints:**
```
# Admin only (requires ADMIN role):
GET  /audit/events                        → Query with filters (service, topic, status, date range, entityType)
GET  /audit/events/{auditId}              → Full detail of a single audit record
GET  /audit/trace/{correlationId}         → All events in a request flow, ordered by time
GET  /audit/entity/{entityType}/{entityId} → All events for a specific ORDER / PAYMENT / USER
GET  /audit/stats                         → Aggregated counts (by service, by status, by eventType)
GET  /audit/failed                        → All FAILED events (supports re-trigger in future)

# Query params for GET /audit/events:
?sourceService=payment-service
?eventCategory=NOTIFICATION
?status=FAILED
?from=2024-01-01&to=2024-01-31
?kafkaTopic=order.created
?userId=user_id
?page=0&size=50
```

---

**MongoDB Indexes:**
```javascript
db.event_logs.createIndex({ correlationId: 1, createdAt: 1 })
db.event_logs.createIndex({ eventType: 1, createdAt: -1 })
db.event_logs.createIndex({ sourceService: 1, targetService: 1, createdAt: -1 })
db.event_logs.createIndex({ entityId: 1, entityType: 1, createdAt: -1 })
db.event_logs.createIndex({ status: 1, createdAt: -1 })
db.event_logs.createIndex({ userId: 1, createdAt: -1 })
db.event_logs.createIndex({ kafkaTopic: 1, createdAt: -1 })
```

---

**What each service logs to `audit.events`:**

| Service | What it logs | eventCategory |
|---|---|---|
| `notification-service` | Every SMTP/SMS/FCM attempt: recipient, subject/body, status (SENT/FAILED), error | `NOTIFICATION` |
| `payment-service` | Razorpay webhook received: full payload + headers; HMAC verification result; `payment.success` or `payment.failed` published | `PAYMENT_GATEWAY` + `WEBHOOK` |
| `payment-service` | Razorpay order creation: request payload, response (razorpayOrderId), duration | `PAYMENT_GATEWAY` |
| `order-service` | Feign call to `inventory-service` reserve/release: request + response + duration | `FEIGN_CALL` |
| `order-service` | Feign call to `coupon-service` validate: request + response + duration | `FEIGN_CALL` |
| `cart-service` | Feign call to `product-service` price check: request + response | `FEIGN_CALL` |
| All services | All Kafka messages are captured passively via spy consumer — no explicit publish needed | `KAFKA` |

---

**How services publish to `audit.events`:**
```java
// In notification-service after sending email:
AuditEvent event = AuditEvent.builder()
    .correlationId(message.getHeaders().get("X-Correlation-Id"))
    .eventCategory("NOTIFICATION")
    .eventType("EMAIL_SENT")
    .sourceService("notification-service")
    .targetAction("SMTP_SEND")
    .requestPayload(Map.of("recipient", email, "subject", subject))
    .status(success ? "SUCCESS" : "FAILED")
    .errorMessage(success ? null : smtpError)
    .processingTimeMs(elapsed)
    .entityId(orderId)
    .entityType("ORDER")
    .build();

kafkaTemplate.send("audit.events", event);
```

---

### API Gateway — Correlation ID Injection

Add to `JwtAuthFilter.java` (or a separate filter):
```java
// If X-Correlation-Id not present, generate one
String correlationId = request.getHeaders().getFirst("X-Correlation-Id");
if (correlationId == null || correlationId.isEmpty()) {
    correlationId = UUID.randomUUID().toString();
}
// Forward to all downstream services
exchange.getRequest().mutate()
    .header("X-Correlation-Id", correlationId)
    .build();
```

All services must forward `X-Correlation-Id` in Kafka message headers when publishing events.

---

## 3. DATABASE DESIGN (MongoDB Atlas)

Each service has its own database (database-per-service pattern):

```
auth_db          → users (auth info only)
user_db          → user_profiles, addresses
product_db       → products, categories, brands
inventory_db     → stock, stock_movements
order_db         → orders
payment_db       → transactions, refunds
delivery_db      → delivery_tasks, delivery_partners
coupon_db        → coupons, coupon_usage
review_db        → reviews
notification_db  → notification_logs
audit_db         → event_logs
```

### Order Document Schema:
```json
{
  "_id": "order_id",
  "orderNumber": "BLK-20240101-0001",
  "userId": "user_id",
  "items": [
    {
      "productId": "prod_id",
      "productName": "Amul Butter 500g",
      "productImage": "url",
      "quantity": 2,
      "unitPrice": 52.0,
      "totalPrice": 104.0
    }
  ],
  "deliveryAddress": {
    "flatNo": "B204",
    "building": "Green Valley",
    "area": "Koramangala",
    "city": "Bangalore",
    "pincode": "560034",
    "lat": 12.9352,
    "lng": 77.6245
  },
  "itemsTotal": 184.0,
  "deliveryFee": 0.0,
  "couponDiscount": 18.0,
  "couponCode": "SAVE10",
  "totalAmount": 166.0,
  "paymentMethod": "UPI",
  "paymentStatus": "PAID",
  "status": "CONFIRMED",
  "statusHistory": [
    { "status": "PENDING", "timestamp": "2024-01-01T10:00:00Z" },
    { "status": "CONFIRMED", "timestamp": "2024-01-01T10:01:00Z" }
  ],
  "estimatedDelivery": "2024-01-01T10:15:00Z",
  "deliveryPartnerId": "dp_id",
  "createdAt": "2024-01-01T10:00:00Z",
  "updatedAt": "2024-01-01T10:01:00Z"
}
```

### Key Indexes:
```javascript
// products - text search
db.products.createIndex({ name: "text", tags: "text", brand: "text" })
db.products.createIndex({ "category.slug": 1, isAvailable: 1 })
db.products.createIndex({ sellingPrice: 1 })

// orders - user's orders, fast lookup
db.orders.createIndex({ userId: 1, createdAt: -1 })
db.orders.createIndex({ status: 1, createdAt: -1 })
db.orders.createIndex({ orderNumber: 1 }, { unique: true })

// inventory - fast stock check
db.stock.createIndex({ productId: 1 }, { unique: true })

// addresses - per user
db.addresses.createIndex({ userId: 1 })

// coupons
db.coupons.createIndex({ code: 1 }, { unique: true })
db.coupon_usage.createIndex({ couponId: 1, userId: 1 })

// reviews
db.reviews.createIndex({ productId: 1, createdAt: -1 })
db.reviews.createIndex({ orderId: 1, userId: 1 }, { unique: true })

// delivery
db.delivery_tasks.createIndex({ orderId: 1 }, { unique: true })
db.delivery_tasks.createIndex({ deliveryPartnerId: 1, status: 1 })
```

---

## 3.1 ENTITY SCHEMAS — ALL FIELDS

> Every field listed with: **Type | Required | Default | Notes**
> 18 collections across 11 databases (including audit_db)

---

### Entity 1: AuthUser — `auth_db.users`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | MongoDB primary key |
| `userId` | String (UUID) | yes | — | Shared identifier across ALL services |
| `email` | String | yes | — | Unique, indexed, used as login username |
| `password` | String | yes | — | BCrypt hashed, never returned in response |
| `roles` | List\<String\> | yes | ["CUSTOMER"] | Enum: CUSTOMER, ADMIN, DELIVERY_PARTNER |
| `isVerified` | Boolean | yes | false | Becomes true after email OTP verification |
| `isActive` | Boolean | yes | true | false = account banned/deactivated |
| `lastLoginAt` | DateTime | no | null | Updated on every successful login |
| `createdAt` | DateTime | yes | now | Auto-set on insert |
| `updatedAt` | DateTime | yes | now | Auto-set on update |

---

### Entity 2: UserProfile — `user_db.user_profiles`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | MongoDB primary key |
| `userId` | String (UUID) | yes | — | Same as AuthUser.userId, unique index |
| `firstName` | String | yes | — | Max 50 chars |
| `lastName` | String | yes | — | Max 50 chars |
| `email` | String | yes | — | Denormalized from auth-service |
| `phone` | String | yes | — | 10-digit mobile number |
| `profileImageUrl` | String | no | null | Cloudinary URL |
| `dateOfBirth` | Date | no | null | For age-restricted products in future |
| `gender` | String | no | null | Enum: MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY |
| `isPhoneVerified` | Boolean | yes | false | For future OTP on phone |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 3: Address — `user_db.addresses`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `addressId` | String (UUID) | yes | — | |
| `userId` | String | yes | — | Foreign key → UserProfile.userId |
| `label` | String | yes | — | Enum: HOME, WORK, OTHER. Max 20 chars |
| `recipientName` | String | yes | — | Who receives the delivery |
| `recipientPhone` | String | yes | — | Contact number for delivery partner |
| `flatNo` | String | yes | — | Flat/House/Door number |
| `building` | String | yes | — | Building/Society/Apartment name |
| `street` | String | no | null | Street/Road name |
| `area` | String | yes | — | Locality/Area name |
| `city` | String | yes | — | City name |
| `state` | String | yes | — | State name |
| `pincode` | String | yes | — | 6-digit postal code |
| `landmark` | String | no | null | e.g. "Near Coffee Day" |
| `lat` | Double | yes | — | GPS latitude for map pin |
| `lng` | Double | yes | — | GPS longitude for map pin |
| `isDefault` | Boolean | yes | false | Only one address can be default per user |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 4: Category — `product_db.categories`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `categoryId` | String (UUID) | yes | — | |
| `name` | String | yes | — | Unique, e.g. "Dairy & Eggs" |
| `slug` | String | yes | — | Unique URL key, e.g. "dairy-eggs" |
| `description` | String | no | null | Shown on category page |
| `imageUrl` | String | yes | — | Category icon/banner (Cloudinary) |
| `parentCategoryId` | String | no | null | null = top-level category |
| `parentCategoryName` | String | no | null | Denormalized for display |
| `displayOrder` | Integer | yes | 0 | Sort order in UI horizontal scroll |
| `isActive` | Boolean | yes | true | false = hidden from storefront |
| `createdBy` | String | yes | — | Admin userId who created it |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 5: Brand — `product_db.brands`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `brandId` | String (UUID) | yes | — | |
| `name` | String | yes | — | Unique, e.g. "Amul" |
| `slug` | String | yes | — | Unique, e.g. "amul" |
| `logoUrl` | String | no | null | Brand logo (Cloudinary) |
| `isActive` | Boolean | yes | true | |
| `createdAt` | DateTime | yes | now | |

---

### Entity 6: Product — `product_db.products`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `productId` | String (UUID) | yes | — | |
| `name` | String | yes | — | Indexed for text search. Max 200 chars |
| `slug` | String | yes | — | Unique URL key, e.g. "amul-butter-500g" |
| `description` | String | no | null | Long description for product detail page |
| `shortDescription` | String | no | null | Max 100 chars, shown on product card |
| `images` | List\<String\> | yes | — | Min 1 image URL (Cloudinary). First = thumbnail |
| `thumbnailUrl` | String | yes | — | First image or dedicated square thumbnail |
| `categoryId` | String | yes | — | Ref → Category.categoryId |
| `categoryName` | String | yes | — | Denormalized for display/filtering |
| `categorySlug` | String | yes | — | Denormalized for fast category filter queries |
| `brandId` | String | no | null | Ref → Brand.brandId |
| `brandName` | String | no | null | Denormalized |
| `mrp` | Double | yes | — | Maximum Retail Price (original price). mrp ≥ sellingPrice |
| `sellingPrice` | Double | yes | — | Actual price customer pays. Must be ≤ mrp |
| `discountPercent` | Integer | yes | 0 | Calculated: floor((mrp-sellingPrice)/mrp × 100) |
| `unit` | String | yes | — | e.g. "500ml", "1 kg", "6 pack", "250g" |
| `weightInGrams` | Double | no | null | For delivery logistics |
| `tags` | List\<String\> | yes | [] | For full-text search, e.g. ["milk","dairy","amul"] |
| `countryOfOrigin` | String | no | null | e.g. "India" |
| `expiryInfo` | String | no | null | e.g. "Best before 6 months from manufacture" |
| `nutritionInfo` | String | no | null | Free text or JSON string |
| `isFeatured` | Boolean | yes | false | Shown in "Best Sellers" / featured sections |
| `isAvailable` | Boolean | yes | true | false = Out of stock (set by inventory-service) |
| `avgRating` | Double | yes | 0.0 | Updated by review-service after each review |
| `reviewCount` | Integer | yes | 0 | Updated by review-service |
| `createdBy` | String | yes | — | Admin userId |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 7: Stock — `inventory_db.stock`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `productId` | String | yes | — | Unique index, ref → Product.productId |
| `productName` | String | yes | — | Denormalized for admin reports |
| `availableQty` | Integer | yes | 0 | Qty customers can actually buy. Never < 0 |
| `reservedQty` | Integer | yes | 0 | Held for pending/confirmed orders |
| `totalQty` | Integer | yes | 0 | availableQty + reservedQty |
| `lowStockThreshold` | Integer | yes | 10 | Kafka alert when availableQty ≤ this |
| `unit` | String | yes | — | Same unit as Product.unit |
| `lastRestockedAt` | DateTime | no | null | When stock was last added |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 8: StockMovement — `inventory_db.stock_movements`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `productId` | String | yes | — | Ref → Stock.productId |
| `type` | String | yes | — | Enum: RESTOCK, SALE, RESERVE, RELEASE, ADJUSTMENT, RETURN |
| `quantity` | Integer | yes | — | Positive = added, Negative = deducted |
| `previousAvailableQty` | Integer | yes | — | Snapshot before this movement |
| `newAvailableQty` | Integer | yes | — | Snapshot after this movement |
| `orderId` | String | no | null | Linked order (for SALE/RESERVE/RELEASE/RETURN) |
| `reason` | String | no | null | Free text, e.g. "Monthly restock", "Damaged goods" |
| `performedBy` | String | yes | — | Admin userId or "SYSTEM" (for automated events) |
| `createdAt` | DateTime | yes | now | Immutable audit log — never updated |

---

### Entity 9: Order — `order_db.orders`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `orderId` | String (UUID) | yes | — | |
| `orderNumber` | String | yes | — | Human-readable: BLK-YYYYMMDD-XXXX. Unique index |
| `userId` | String | yes | — | Ref → AuthUser.userId |
| `userEmail` | String | yes | — | Denormalized (for notifications without calling user-service) |
| `userPhone` | String | yes | — | Denormalized |
| `items` | List\<OrderItem\> | yes | — | Snapshot of products at time of order (price may change later) |
| `items[].productId` | String | yes | — | |
| `items[].productName` | String | yes | — | Snapshot |
| `items[].thumbnailUrl` | String | yes | — | Snapshot |
| `items[].unit` | String | yes | — | Snapshot |
| `items[].quantity` | Integer | yes | — | Min 1 |
| `items[].mrp` | Double | yes | — | Snapshot of mrp at order time |
| `items[].unitPrice` | Double | yes | — | Snapshot of sellingPrice at order time |
| `items[].totalPrice` | Double | yes | — | unitPrice × quantity |
| `deliveryAddress` | Object | yes | — | Snapshot — copied from user's selected address |
| `deliveryAddress.recipientName` | String | yes | — | |
| `deliveryAddress.recipientPhone` | String | yes | — | |
| `deliveryAddress.flatNo` | String | yes | — | |
| `deliveryAddress.building` | String | yes | — | |
| `deliveryAddress.area` | String | yes | — | |
| `deliveryAddress.city` | String | yes | — | |
| `deliveryAddress.state` | String | yes | — | |
| `deliveryAddress.pincode` | String | yes | — | |
| `deliveryAddress.landmark` | String | no | null | |
| `deliveryAddress.lat` | Double | yes | — | |
| `deliveryAddress.lng` | Double | yes | — | |
| `itemsTotal` | Double | yes | — | Sum of all items[].totalPrice |
| `deliveryFee` | Double | yes | 0.0 | 0 if order ≥ free delivery threshold |
| `couponCode` | String | no | null | Applied coupon code |
| `couponDiscount` | Double | yes | 0.0 | Discount amount from coupon |
| `totalAmount` | Double | yes | — | itemsTotal + deliveryFee - couponDiscount |
| `paymentMethod` | String | yes | — | Enum: UPI, CARD, NET_BANKING, COD |
| `paymentStatus` | String | yes | PENDING | Enum: PENDING, PAID, FAILED, REFUNDED, PARTIALLY_REFUNDED |
| `paymentTransactionId` | String | no | null | Ref → Transaction.transactionId |
| `status` | String | yes | PENDING | Enum: PENDING, PAYMENT_PENDING, CONFIRMED, PREPARING, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, PAYMENT_FAILED |
| `statusHistory` | List\<StatusEvent\> | yes | [] | Append-only audit trail |
| `statusHistory[].status` | String | yes | — | |
| `statusHistory[].timestamp` | DateTime | yes | — | |
| `statusHistory[].note` | String | no | null | e.g. "Customer cancelled" |
| `cancellationReason` | String | no | null | Required if status = CANCELLED |
| `cancelledBy` | String | no | null | "USER" or "ADMIN" |
| `deliveryPartnerId` | String | no | null | Assigned when OUT_FOR_DELIVERY |
| `estimatedDeliveryAt` | DateTime | no | null | Set when order is confirmed |
| `deliveredAt` | DateTime | no | null | Set when status = DELIVERED |
| `isReviewPending` | Boolean | yes | false | Set true after delivery, false after user reviews |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 10: Transaction — `payment_db.transactions`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `transactionId` | String (UUID) | yes | — | Unique |
| `orderId` | String | yes | — | Unique ref → Order.orderId |
| `userId` | String | yes | — | |
| `amount` | Double | yes | — | Total payment amount in INR |
| `currency` | String | yes | "INR" | ISO 4217 currency code |
| `paymentMethod` | String | yes | — | Enum: UPI, CARD, NET_BANKING, COD, WALLET |
| `paymentGateway` | String | yes | "RAZORPAY" | For future multi-gateway support |
| `gatewayOrderId` | String | yes | — | Razorpay order_id returned on initiation |
| `gatewayPaymentId` | String | no | null | Razorpay payment_id — populated after payment |
| `gatewaySignature` | String | no | null | Razorpay signature for verification |
| `status` | String | yes | INITIATED | Enum: INITIATED, SUCCESS, FAILED, REFUNDED, PARTIALLY_REFUNDED |
| `failureReason` | String | no | null | Razorpay error description if failed |
| `paidAt` | DateTime | no | null | Timestamp when payment confirmed |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 11: Refund — `payment_db.refunds`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `refundId` | String (UUID) | yes | — | |
| `transactionId` | String | yes | — | Ref → Transaction.transactionId |
| `orderId` | String | yes | — | |
| `userId` | String | yes | — | |
| `amount` | Double | yes | — | Amount to refund (may be partial) |
| `reason` | String | yes | — | Enum: ORDER_CANCELLED, PAYMENT_ERROR, CUSTOMER_REQUEST, ITEM_UNAVAILABLE |
| `initiatedBy` | String | yes | — | "USER", "ADMIN", or "SYSTEM" |
| `gatewayRefundId` | String | no | null | Razorpay refund ID |
| `status` | String | yes | INITIATED | Enum: INITIATED, PROCESSED, FAILED |
| `failureReason` | String | no | null | Why refund failed |
| `processedAt` | DateTime | no | null | When Razorpay confirmed refund |
| `createdAt` | DateTime | yes | now | |

---

### Entity 12: DeliveryPartner — `delivery_db.delivery_partners`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `partnerId` | String (UUID) | yes | — | Same as AuthUser.userId |
| `name` | String | yes | — | |
| `email` | String | yes | — | |
| `phone` | String | yes | — | |
| `profileImageUrl` | String | no | null | |
| `vehicleType` | String | yes | — | Enum: BICYCLE, MOTORCYCLE, SCOOTER, CAR |
| `vehicleNumber` | String | no | null | e.g. "KA-01-AB-1234" |
| `isAvailable` | Boolean | yes | true | true = ready to accept new deliveries |
| `isActive` | Boolean | yes | true | false = deactivated/suspended |
| `avgRating` | Double | yes | 5.0 | Calculated from customer ratings |
| `totalDeliveries` | Integer | yes | 0 | Lifetime delivery count |
| `currentLat` | Double | no | null | Live GPS latitude |
| `currentLng` | Double | no | null | Live GPS longitude |
| `lastLocationUpdatedAt` | DateTime | no | null | |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 13: DeliveryTask — `delivery_db.delivery_tasks`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `taskId` | String (UUID) | yes | — | |
| `orderId` | String | yes | — | Unique ref → Order.orderId |
| `deliveryPartnerId` | String | no | null | null until assigned |
| `status` | String | yes | UNASSIGNED | Enum: UNASSIGNED, ASSIGNED, PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED |
| `pickupAddress` | Object | yes | — | Store/warehouse address (static config) |
| `pickupAddress.name` | String | yes | — | Store name |
| `pickupAddress.lat` | Double | yes | — | |
| `pickupAddress.lng` | Double | yes | — | |
| `customerAddress` | Object | yes | — | Snapshot from order.deliveryAddress |
| `customerAddress.recipientName` | String | yes | — | |
| `customerAddress.phone` | String | yes | — | |
| `customerAddress.fullAddress` | String | yes | — | Formatted for display |
| `customerAddress.lat` | Double | yes | — | |
| `customerAddress.lng` | Double | yes | — | |
| `currentLat` | Double | no | null | Live rider location for tracking |
| `currentLng` | Double | no | null | |
| `estimatedPickupAt` | DateTime | no | null | |
| `estimatedDeliveryAt` | DateTime | no | null | |
| `actualPickupAt` | DateTime | no | null | |
| `actualDeliveryAt` | DateTime | no | null | Set when status = DELIVERED |
| `failureReason` | String | no | null | e.g. "Customer not available", "Wrong address" |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 14: Coupon — `coupon_db.coupons`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `couponId` | String (UUID) | yes | — | |
| `code` | String | yes | — | Unique, uppercase, indexed. e.g. "SAVE10" |
| `description` | String | yes | — | Shown to user, e.g. "10% off on orders above ₹149" |
| `type` | String | yes | — | Enum: FLAT, PERCENT, FIRST_ORDER, FREE_DELIVERY |
| `discountValue` | Double | yes | — | Amount (for FLAT) or percentage (for PERCENT/FIRST_ORDER) |
| `maxDiscountAmount` | Double | no | null | Cap for PERCENT type, e.g. max ₹100 off |
| `minOrderAmount` | Double | yes | 0.0 | Cart must be ≥ this to apply |
| `totalUsageLimit` | Integer | no | null | null = unlimited total uses |
| `perUserUsageLimit` | Integer | yes | 1 | How many times one user can use this |
| `usedCount` | Integer | yes | 0 | Total redemptions so far |
| `applicableOnFirstOrderOnly` | Boolean | yes | false | If true, only works for user's first order |
| `validFrom` | DateTime | yes | — | Coupon becomes active from this time |
| `validUntil` | DateTime | yes | — | Coupon expires after this time |
| `isActive` | Boolean | yes | true | Admin can disable without deleting |
| `createdBy` | String | yes | — | Admin userId |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 15: CouponUsage — `coupon_db.coupon_usage`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `couponId` | String | yes | — | Ref → Coupon.couponId |
| `couponCode` | String | yes | — | Denormalized for easy lookup |
| `userId` | String | yes | — | Who used the coupon |
| `orderId` | String | yes | — | Which order it was applied to. Unique per coupon+user |
| `discountApplied` | Double | yes | — | Actual discount amount given |
| `usedAt` | DateTime | yes | now | |

---

### Entity 16: Review — `review_db.reviews`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `reviewId` | String (UUID) | yes | — | |
| `productId` | String | yes | — | Ref → Product.productId |
| `productName` | String | yes | — | Denormalized |
| `orderId` | String | yes | — | Only delivered orders can be reviewed |
| `userId` | String | yes | — | Compound unique index: {orderId + userId} prevents double review |
| `userDisplayName` | String | yes | — | e.g. "R. Kumar" — partial name for privacy |
| `rating` | Integer | yes | — | 1 to 5 (inclusive) |
| `title` | String | no | null | Optional headline, max 100 chars |
| `comment` | String | no | null | Optional detailed review, max 1000 chars |
| `images` | List\<String\> | no | [] | User-uploaded review photos (Cloudinary) |
| `isVerifiedPurchase` | Boolean | yes | true | Always true — review tied to an order |
| `isVisible` | Boolean | yes | true | Admin can set false to hide inappropriate reviews |
| `helpfulCount` | Integer | yes | 0 | Users can mark a review as helpful |
| `createdAt` | DateTime | yes | now | |
| `updatedAt` | DateTime | yes | now | |

---

### Entity 17: NotificationLog — `notification_db.notification_logs`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | |
| `logId` | String (UUID) | yes | — | |
| `userId` | String | yes | — | Recipient user |
| `type` | String | yes | — | Enum: EMAIL, SMS, PUSH |
| `eventType` | String | yes | — | e.g. ORDER_CONFIRMED, PAYMENT_SUCCESS, PASSWORD_RESET |
| `recipient` | String | yes | — | Email address, phone number, or FCM device token |
| `subject` | String | no | null | Email subject line (null for SMS/Push) |
| `body` | String | yes | — | Full message content |
| `status` | String | yes | PENDING | Enum: PENDING, SENT, FAILED |
| `failureReason` | String | no | null | SMTP error, Twilio error, etc. |
| `retryCount` | Integer | yes | 0 | Number of send retries attempted |
| `sentAt` | DateTime | no | null | Populated when status = SENT |
| `createdAt` | DateTime | yes | now | |

---

### Entity 18: AuditEventLog — `audit_db.event_logs`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `_id` | ObjectId | auto | — | MongoDB primary key |
| `auditId` | String (UUID) | yes | — | Unique identifier for this audit record |
| `correlationId` | String (UUID) | yes | — | Links all audit records in the same end-to-end request flow; injected by gateway |
| `eventCategory` | String | yes | — | Enum: KAFKA, NOTIFICATION, PAYMENT_GATEWAY, FEIGN_CALL, WEBHOOK |
| `eventType` | String | yes | — | e.g. `order.created`, `EMAIL_SENT`, `PAYMENT_WEBHOOK`, `FEIGN_RESERVE_STOCK` |
| `sourceService` | String | yes | — | Service that published/sent the event |
| `sourceAction` | String | no | null | Endpoint or method that triggered it (e.g. `POST /orders`) |
| `targetService` | String | no | null | Service that received/consumed the event |
| `targetAction` | String | no | null | Action taken by the consumer (e.g. `KAFKA_CONSUME`, `SMTP_SEND`, `FEIGN_CALL`) |
| `kafkaTopic` | String | no | null | Kafka topic name (null for non-Kafka events) |
| `kafkaPartition` | Integer | no | null | Kafka partition the message was written to |
| `kafkaOffset` | Long | no | null | Kafka offset within the partition |
| `kafkaConsumerGroup` | String | no | null | Consumer group that processed the message |
| `kafkaKey` | String | no | null | Kafka message key (usually entityId) |
| `kafkaHeaders` | Map | no | {} | Kafka message headers (X-Correlation-Id, X-User-Id, etc.) |
| `requestPayload` | Object | no | null | Full message/request body (Kafka payload, Feign request, webhook body) |
| `responsePayload` | Object | no | null | Response body or processing result |
| `responseTopic` | String | no | null | Kafka topic the response was published to (if applicable) |
| `status` | String | yes | PENDING | Enum: SUCCESS, FAILED, PENDING, RETRY |
| `errorCode` | String | no | null | Application-level error code on failure |
| `errorMessage` | String | no | null | Error detail (SMTP error, Twilio error, HTTP 500, etc.) |
| `processingTimeMs` | Long | no | null | How long the target took to process (ms) |
| `retryCount` | Integer | yes | 0 | Number of retry attempts made |
| `publishedAt` | DateTime | no | null | When the event was published by the source |
| `consumedAt` | DateTime | no | null | When the event was picked up by the consumer |
| `processedAt` | DateTime | no | null | When processing completed |
| `userId` | String | no | null | User ID associated with the event (if applicable) |
| `entityId` | String | no | null | Primary entity ID (orderId, paymentId, userId, etc.) |
| `entityType` | String | no | null | Enum: ORDER, PAYMENT, USER, NOTIFICATION, INVENTORY, COUPON |
| `createdAt` | DateTime | yes | now | When this audit record was written to MongoDB |

---

## 4. KAFKA EVENTS

**Topics:**
```
user.registered          → Consumers: notification-service (welcome email + OTP)
user.password.reset      → Consumers: notification-service (send reset link email)
order.created            → Consumers: payment-service, notification-service
order.confirmed          → Consumers: inventory-service, notification-service, delivery-service, cart-service (clear cart)
order.cancelled          → Consumers: inventory-service (release stock), payment-service (refund), notification-service
order.status.updated     → Consumers: notification-service
order.delivered          → Consumers: notification-service (rate us email), review-service (unlock review for product)
payment.success          → Consumers: order-service (confirm order), notification-service
payment.failed           → Consumers: order-service (mark failed), notification-service
inventory.low            → Consumers: notification-service (admin alert)
inventory.out            → Consumers: product-service (mark isAvailable=false), notification-service
audit.events             → Consumers: audit-service ONLY (notification outcomes, Feign results, payment gateway events)
```

**Note on audit capture:** The `audit-service` also subscribes to **all** topics above under its own `audit-consumer-group`. Every Kafka message is passively captured without any changes to publisher or other consumer code. The `audit.events` topic is only for non-Kafka events that services explicitly publish (notification delivery result, Feign call result, payment webhook detail).

**Event Schema Example:**
```json
{
  "eventId": "uuid",
  "eventType": "order.created",
  "timestamp": "2024-01-01T10:00:00Z",
  "payload": {
    "orderId": "order_id",
    "userId": "user_id",
    "totalAmount": 256.50,
    "items": [...]
  }
}
```

---

## 5. REDIS USAGE

| Use Case | Key Pattern | TTL |
|---|---|---|
| Cart data | `cart:{userId}` | 7 days |
| JWT blacklist (logout) | `blacklist:{token}` | token expiry |
| Refresh tokens | `refresh:{userId}` | 30 days |
| Rate limiting | `rate:{userId}:{endpoint}` | 1 minute |
| Email OTP (signup) | `otp:{email}` | 5 minutes |
| Password reset token | `pwd_reset:{uuid_token}` | 15 minutes |
| Product cache | `products:category:{slug}` | 10 minutes |
| Top/featured products | `products:featured` | 30 minutes |
| Coupon validation cache | `coupon:{code}` | 5 minutes |
| Stock cache | `stock:{productId}` | 2 minutes |

---

## 6. INTER-SERVICE COMMUNICATION (Feign Clients)

Services communicate synchronously via Feign clients (Spring Cloud OpenFeign) for real-time data needs, and asynchronously via Kafka for event-driven flows.

```
order-service      → inventory-service   (Feign) reserve/release stock before confirming order
order-service      → coupon-service      (Feign) validate coupon code during checkout
cart-service       → product-service     (Feign) fetch current price + availability
cart-service       → coupon-service      (Feign) validate applied promo code
delivery-service   → user-service        (Feign) get customer delivery address
review-service     → product-service     (Feign) update avg rating on product document
```

**Feign Client Example (order-service calling inventory-service):**
```java
@FeignClient(name = "inventory-service")
public interface InventoryClient {
    @PostMapping("/inventory/reserve")
    ReserveResponse reserveStock(@RequestBody ReserveRequest request);
}
```

**Circuit Breaker:** Each Feign call wrapped with Resilience4j `@CircuitBreaker` — fallback returns error response instead of cascading failure.

---

## 7. API DESIGN

### Common Headers:
```
Authorization: Bearer <jwt_token>
Content-Type: application/json
X-User-Id: <propagated by gateway from JWT>
X-User-Role: <propagated by gateway from JWT>
```

### Common Response Format:
```json
{
  "success": true,
  "data": { ... },
  "message": "Success",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

### Error Response:
```json
{
  "success": false,
  "error": {
    "code": "PRODUCT_NOT_FOUND",
    "message": "Product with id xyz not found",
    "status": 404
  },
  "timestamp": "2024-01-01T10:00:00Z"
}
```

### API Documentation:
Each service exposes Swagger UI via `springdoc-openapi`:
```
http://localhost:{port}/swagger-ui.html
```
API gateway aggregates all service docs at:
```
http://localhost:8080/swagger-ui.html  (combined docs)
```

### Global Exception Handler:
Each service has a `@RestControllerAdvice` GlobalExceptionHandler (same pattern as journalApp) handling:
- `ResourceNotFoundException` → 404
- `ValidationException` → 400
- `UnauthorizedException` → 401
- `InsufficientStockException` → 409
- `PaymentException` → 402
- Generic `Exception` → 500

---

## 8. UI/UX DESIGN

### Color Palette (Blinkit-inspired):
```
Primary:    #F8C200  (Blinkit Yellow)
Accent:     #0C0F0A  (Near Black)
Success:    #2E7D32  (Green)
Danger:     #D32F2F  (Red)
Background: #F5F5F5  (Light Grey)
Card:       #FFFFFF  (White)
Text:       #1A1A1A  (Dark)
Muted:      #757575  (Grey)
```

### Typography:
```
Headings:  Inter Bold / Poppins Bold
Body:      Inter Regular
Prices:    Inter SemiBold
```

---

### SCREEN 0a: LOGIN PAGE

```
┌──────────────────────────────────────────────────────┐
│              🟡 BlinkIt                              │
│         Delivery in 10 minutes                       │
├──────────────────────────────────────────────────────┤
│                                                      │
│  Welcome back!                                       │
│  Sign in to continue                                 │
│                                                      │
│  Email                                               │
│  ┌────────────────────────────────────────────────┐  │
│  │  rahul@email.com                               │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  Password                                            │
│  ┌────────────────────────────────────────────────┐  │
│  │  ••••••••                            [👁 show] │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│                         [Forgot Password?]           │
│                                                      │
│  [          Login          ]                         │
│                                                      │
│  ──────── or ────────                                │
│                                                      │
│  Don't have an account?  [Sign Up]                   │
└──────────────────────────────────────────────────────┘
```

**Behavior:**
- Show/hide password toggle
- "Forgot Password?" → navigates to Forgot Password screen
- On success → redirect to home page
- Validation: email format, password non-empty
- Error toast: "Invalid email or password"

---

### SCREEN 0b: SIGNUP PAGE

```
┌──────────────────────────────────────────────────────┐
│  ← Back         Create Account                      │
├──────────────────────────────────────────────────────┤
│                                                      │
│  Full Name                                           │
│  ┌────────────────────────────────────────────────┐  │
│  │  Rahul Kumar                                   │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  Email                                               │
│  ┌────────────────────────────────────────────────┐  │
│  │  rahul@email.com                               │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  Phone Number                                        │
│  ┌────────────────────────────────────────────────┐  │
│  │  +91  9876543210                               │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  Password                                            │
│  ┌────────────────────────────────────────────────┐  │
│  │  ••••••••                            [👁 show] │  │
│  └────────────────────────────────────────────────┘  │
│  ● ≥ 8 characters  ● 1 uppercase  ● 1 number        │
│                                                      │
│  [          Create Account          ]                │
│                                                      │
│  Already have an account?  [Login]                   │
└──────────────────────────────────────────────────────┘
```

**After signup → OTP Verification screen:**
```
┌──────────────────────────────────────────────────────┐
│  Verify your email                                   │
│  We sent a 6-digit code to rahul@email.com           │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌────┐  ┌────┐  ┌────┐  ┌────┐  ┌────┐  ┌────┐    │
│  │ 1  │  │ 2  │  │ 3  │  │ 4  │  │ 5  │  │ 6  │    │
│  └────┘  └────┘  └────┘  └────┘  └────┘  └────┘    │
│                                                      │
│  [       Verify Email       ]                        │
│                                                      │
│  Didn't receive?  [Resend OTP]  (after 30s)          │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 0c: FORGOT PASSWORD PAGE

```
┌──────────────────────────────────────────────────────┐
│  ← Back         Forgot Password                     │
├──────────────────────────────────────────────────────┤
│                                                      │
│  Enter your registered email address.                │
│  We'll send you a link to reset your password.       │
│                                                      │
│  Email                                               │
│  ┌────────────────────────────────────────────────┐  │
│  │  rahul@email.com                               │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  [      Send Reset Link      ]                       │
│                                                      │
│  ─── After clicking: ────────────────────────────    │
│                                                      │
│  ✅ Reset link sent!                                 │
│  Check your inbox at rahul@email.com                 │
│  Link expires in 15 minutes.                         │
│                                                      │
│  [← Back to Login]                                   │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 0d: RESET PASSWORD PAGE (opened via email link)

```
┌──────────────────────────────────────────────────────┐
│              🟡 BlinkIt                              │
│           Reset your password                        │
├──────────────────────────────────────────────────────┤
│                                                      │
│  (If token invalid/expired:)                         │
│  ❌ This link has expired or is invalid.             │
│     [Request a new reset link]                       │
│                                                      │
│  (If token valid:)                                   │
│  New Password                                        │
│  ┌────────────────────────────────────────────────┐  │
│  │  ••••••••                            [👁 show] │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  Confirm Password                                    │
│  ┌────────────────────────────────────────────────┐  │
│  │  ••••••••                            [👁 show] │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  [      Update Password      ]                       │
│                                                      │
│  ─── After success: ──────────────────────────────   │
│  ✅ Password updated successfully!                   │
│  [Login with new password →]                         │
└──────────────────────────────────────────────────────┘
```

**Behavior:**
- Page loads → frontend calls `GET /auth/reset-password/validate/{token}`
- If 410 (expired/invalid) → show error state immediately
- If 200 (valid) → show new password form
- Passwords must match before enabling submit button

---

### SCREEN 1: HOME PAGE

```
┌──────────────────────────────────────────────────────┐
│  HEADER                                              │
│  ┌──────────┐  ┌──────────────────────────┐  ┌───┐  │
│  │ 🟡 Logo  │  │ 📍 Koramangala, Blore    │  │👤 │  │
│  └──────────┘  └──────────────────────────┘  └───┘  │
├──────────────────────────────────────────────────────┤
│  SEARCH BAR                                          │
│  ┌────────────────────────────────────────────────┐  │
│  │ 🔍 Search for "atta, dal, rice, eggs..."       │  │
│  └────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────┤
│  DELIVERY BADGE                                      │
│  ┌──────────────────────────────────────────────┐    │
│  │ ⚡ Delivery in 10 minutes                    │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  HERO BANNER (Swipeable carousel)                    │
│  ┌──────────────────────────────────────────────┐    │
│  │  🎁  FLAT 20% OFF on first order             │    │
│  │      Use code: FIRST20           [Shop Now]  │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  CATEGORIES (horizontal scroll)                      │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐      │
│  │ 🥛   │ │ 🥦   │ │ 🍎   │ │ 🍞   │ │ 🧹   │  →  │
│  │Dairy │ │Veggies│ │Fruits│ │Bakery│ │Clean │      │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘      │
├──────────────────────────────────────────────────────┤
│  SECTION: "Best Sellers"                             │
│  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐     │
│  │ [img]  │  │ [img]  │  │ [img]  │  │ [img]  │     │
│  │Amul    │  │Brown   │  │Toned   │  │Eggs    │     │
│  │Butter  │  │Bread   │  │Milk    │  │(6 pack)│     │
│  │500g    │  │400g    │  │500ml   │  │        │     │
│  │₹52     │  │₹35     │  │₹28     │  │₹65     │     │
│  │ [+ ADD]│  │ [+ ADD]│  │ [+ ADD]│  │ [+ ADD]│     │
│  └────────┘  └────────┘  └────────┘  └────────┘     │
├──────────────────────────────────────────────────────┤
│  SECTION: "Fruits & Vegetables"   [See All →]        │
│  (Same product grid pattern)                         │
├──────────────────────────────────────────────────────┤
│  SECTION: "Deals of the Day" (yellow banner strip)   │
│  (Same product grid pattern)                         │
├──────────────────────────────────────────────────────┤
│  FLOATING CART BAR (appears when cart has items)     │
│  ┌──────────────────────────────────────────────┐    │
│  │ 🛒 3 items │ ₹156    [View Cart & Checkout →]│    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

**Behavior:**
- Location picker opens a modal with Google Maps or manual pincode entry
- Search bar → live suggestions (debounced 300ms) as user types
- Product ADD button becomes ➕ quantity ➖ inline after first click
- Sticky floating cart bar at bottom when cart is non-empty
- Categories scroll horizontally on mobile

---

### SCREEN 2: CATEGORY / PRODUCT LISTING PAGE

```
┌──────────────────────────────────────────────────────┐
│  ← Dairy & Eggs                         🔍 Filter   │
├──────────────────────────────────────────────────────┤
│  SORT & FILTER ROW                                   │
│  [Relevance ▼] [Price ▼] [Brand ▼] [Discount ▼]    │
├──────────────────────────────────────────────────────┤
│  PRODUCT GRID (2 columns on mobile, 4 on desktop)   │
│                                                      │
│  ┌─────────────────┐  ┌─────────────────┐           │
│  │ [7% OFF] badge  │  │                 │           │
│  │                 │  │   [Product img] │           │
│  │  [Product img]  │  │                 │           │
│  │                 │  │ Amul Cheese     │           │
│  │ Amul Butter     │  │ Slice 200g      │           │
│  │ 500g            │  │                 │           │
│  │ ₹52  ~~₹56~~   │  │ ₹135            │           │
│  │     [+ ADD]     │  │     [+ ADD]     │           │
│  └─────────────────┘  └─────────────────┘           │
│                                                      │
│  ┌─────────────────┐  ┌─────────────────┐           │
│  │ [OUT OF STOCK]  │  │                 │           │
│  │   (greyed out)  │  │   [Product img] │           │
│  │  [Product img]  │  │                 │           │
│  │ Paneer 200g     │  │ Dahi 400g       │           │
│  │ ₹95             │  │ ₹45             │           │
│  │ [Notify Me]     │  │     [+ ADD]     │           │
│  └─────────────────┘  └─────────────────┘           │
└──────────────────────────────────────────────────────┘
```

**Behavior:**
- Filter drawer slides in from right
- Out-of-stock products are greyed with "Notify Me" button
- Infinite scroll pagination
- Filter by price range, brand, discount %

---

### SCREEN 3: PRODUCT DETAIL PAGE

```
┌──────────────────────────────────────────────────────┐
│  ← Back                            🔍  🛒 (3)       │
├──────────────────────────────────────────────────────┤
│  IMAGE CAROUSEL                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │                                                │  │
│  │              [Product Image]                   │  │
│  │                                                │  │
│  │  ○  ●  ○  ○   (dots)                          │  │
│  └────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────┤
│  Amul Full Cream Milk                                │
│  500ml | Dairy                                       │
│                                                      │
│  ₹28  ~~₹30~~  🏷️ 7% OFF                           │
│  ✅ In Stock   ⚡ Delivered in 10 min               │
├──────────────────────────────────────────────────────┤
│  [     ➖ 2 ➕     ] ← Quantity picker in cart      │
│  [ 🛒  Add to Cart  ]                               │
├──────────────────────────────────────────────────────┤
│  PRODUCT DETAILS                                     │
│  Brand: Amul                                         │
│  Weight/Volume: 500ml                                │
│  Country of Origin: India                            │
├──────────────────────────────────────────────────────┤
│  ABOUT                                               │
│  Amul Full Cream Milk is rich in protein...          │
├──────────────────────────────────────────────────────┤
│  YOU MIGHT ALSO LIKE                                 │
│  (Horizontal scroll — similar products)              │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 4: CART PAGE

```
┌──────────────────────────────────────────────────────┐
│  My Cart  (3 items)                                  │
├──────────────────────────────────────────────────────┤
│  DELIVERY ADDRESS                                    │
│  ┌──────────────────────────────────────────────┐    │
│  │ 📍 Home - B204, Green Valley, Koramangala    │    │
│  │          [Change]                            │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  CART ITEMS                                          │
│  ┌──────────────────────────────────────────────┐    │
│  │ [img] Amul Butter 500g           ➖ 2 ➕      │    │
│  │       ₹52/unit                     ₹104      │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ [img] Brown Bread 400g           ➖ 1 ➕      │    │
│  │       ₹35/unit                      ₹35      │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ [img] Dahi 400g                  ➖ 1 ➕      │    │
│  │       ₹45/unit                      ₹45      │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  PROMO CODE                                          │
│  ┌────────────────────────────┐  ┌──────────┐        │
│  │  Enter promo code          │  │  APPLY   │        │
│  └────────────────────────────┘  └──────────┘        │
├──────────────────────────────────────────────────────┤
│  BILL SUMMARY                                        │
│  Items Total:              ₹184                      │
│  Delivery fee:             FREE (>₹149)              │
│  Promo (SAVE10):           -₹18                      │
│  ─────────────────────────────                       │
│  To Pay:                   ₹166                      │
├──────────────────────────────────────────────────────┤
│  [      Proceed to Pay  ₹166      ]                  │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 5: ADDRESS SELECTION (During Checkout)

```
┌──────────────────────────────────────────────────────┐
│  ← Select Delivery Address                           │
├──────────────────────────────────────────────────────┤
│  SAVED ADDRESSES                                     │
│  ┌──────────────────────────────────────────────┐    │
│  │ ✅ 🏠 Home (Default)            [Edit]        │    │
│  │ B204, Green Valley, Koramangala              │    │
│  │ Bangalore - 560034                           │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ ○ 💼 Work                       [Edit]        │    │
│  │ 3rd Floor, Tech Park, Whitefield             │    │
│  │ Bangalore - 560066                           │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  [+ Add New Address]                                 │
├──────────────────────────────────────────────────────┤
│  LIVE MAP (Google Maps embed)                        │
│  ┌──────────────────────────────────────────────┐    │
│  │          [🗺️ Map View — Pin Location]         │    │
│  │                    📍                        │    │
│  └──────────────────────────────────────────────┘    │
│  [Confirm Location & Continue]                       │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 6: PAYMENT PAGE

```
┌──────────────────────────────────────────────────────┐
│  ← Payment                                           │
├──────────────────────────────────────────────────────┤
│  ORDER SUMMARY                                       │
│  📦 3 items • Delivering to Home                    │
│  ⚡ In ~10 minutes                                   │
├──────────────────────────────────────────────────────┤
│  PAYMENT METHODS                                     │
│                                                      │
│  ● 💳 Credit/Debit Card                             │
│    ○ 📱 UPI (GPay, PhonePe, Paytm)                  │
│    ○ 🏦 Net Banking                                  │
│    ○ 💵 Cash on Delivery                            │
│                                                      │
├──────────────────────────────────────────────────────┤
│  UPI ID  (if UPI selected)                          │
│  ┌────────────────────────────────────────────────┐  │
│  │  yourname@okicici                              │  │
│  └────────────────────────────────────────────────┘  │
│  [Pay with GPay] [Pay with PhonePe] [Pay with Paytm]│
├──────────────────────────────────────────────────────┤
│  BILL TOTAL: ₹166                                    │
│  [      PAY ₹166 SECURELY      ]                    │
│  🔒 Secured by Razorpay                             │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 7: ORDER CONFIRMATION & TRACKING

```
┌──────────────────────────────────────────────────────┐
│  ✅ Order Placed Successfully!                       │
│  Order #BLK-20240101-001                             │
├──────────────────────────────────────────────────────┤
│  LIVE TRACKING                                       │
│  ┌──────────────────────────────────────────────┐    │
│  │          [🗺️ Live Map View]                   │    │
│  │          🏪────────────🏠                     │    │
│  │          Store       You                     │    │
│  │                 🛵 (rider)                   │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  STATUS TIMELINE                                     │
│  ✅ Order Placed        10:00 AM                     │
│  ✅ Order Confirmed     10:01 AM                     │
│  ✅ Packing Order       10:03 AM                     │
│  🔄 Out for Delivery    ~10:08 AM (estimated)        │
│  ○  Delivered           ~10:15 AM (estimated)        │
├──────────────────────────────────────────────────────┤
│  DELIVERY PARTNER                                    │
│  ┌──────────────────────────────────────────────┐    │
│  │ [Photo] Rahul K.    ⭐ 4.8     📞 Call       │    │
│  └──────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────┤
│  [Cancel Order]  (only before Out for Delivery)     │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 8: USER PROFILE

```
┌──────────────────────────────────────────────────────┐
│  My Account                                          │
├──────────────────────────────────────────────────────┤
│  [Avatar] Rahul Kumar                                │
│           rahul@email.com | +91 9876543210           │
│           [Edit Profile]                             │
├──────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────┐ │
│  │ 🛍️  My Orders                              >   │ │
│  ├─────────────────────────────────────────────────┤ │
│  │ 📍  Saved Addresses                        >   │ │
│  ├─────────────────────────────────────────────────┤ │
│  │ 💳  Payment Methods                        >   │ │
│  ├─────────────────────────────────────────────────┤ │
│  │ 🎁  Coupons & Offers                       >   │ │
│  ├─────────────────────────────────────────────────┤ │
│  │ 🔔  Notifications                          >   │ │
│  ├─────────────────────────────────────────────────┤ │
│  │ ❓  Help & Support                         >   │ │
│  └─────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────┤
│  [  🚪 Logout  ]                                     │
└──────────────────────────────────────────────────────┘
```

---

### SCREEN 9: MY ORDERS LIST

```
┌──────────────────────────────────────────────────────┐
│  ← My Orders                                         │
├──────────────────────────────────────────────────────┤
│  FILTER TABS: [All] [Active] [Delivered] [Cancelled] │
├──────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────┐    │
│  │ Order #BLK-20240101-001      10 Jan, 10:00 AM│    │
│  │ 3 items · ₹166               ✅ DELIVERED    │    │
│  │ [img][img][img]              [Rate Order →]  │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ Order #BLK-20231228-089      28 Dec, 6:15 PM │    │
│  │ 2 items · ₹94             🔄 OUT FOR DELIVERY│    │
│  │ [img][img]                  [Track Order →]  │    │
│  └──────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────┐    │
│  │ Order #BLK-20231220-041      20 Dec, 11:30 AM│    │
│  │ 5 items · ₹423               ❌ CANCELLED    │    │
│  │ [img][img][img]+2            [Reorder →]     │    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

**Behavior:**
- Clicking any order card → opens Order Detail / Tracking screen
- Active orders show "Track Order" button
- Delivered orders show "Rate Order" button
- Cancelled orders show "Reorder" button (pre-fills cart with same items)

---

### SCREEN 10: LIVE TRACKING (WebSocket)

```
Frontend connects to:
ws://api.blinkit.com/ws/delivery/track/{orderId}

delivery-service sends location updates every 5 seconds:
{
  "lat": 12.9360,
  "lng": 77.6250,
  "status": "OUT_FOR_DELIVERY",
  "estimatedMinutes": 6
}

Frontend updates rider marker on Google Maps in real-time.
```

---

## 9. ADMIN PANEL DESIGN

### Admin Panel — Layout:
```
┌─────────────────────────────────────────────────────────────────────┐
│  🟡 BlinkIt Admin                              👤 Admin  [Logout]   │
├─────────────────┬───────────────────────────────────────────────────┤
│  SIDEBAR        │  CONTENT AREA                                      │
│                 │                                                     │
│  📊 Dashboard   │                                                     │
│  📦 Products    │                                                     │
│  📋 Orders      │                                                     │
│  👥 Users       │                                                     │
│  📦 Inventory   │                                                     │
│  🚚 Delivery    │                                                     │
│  🏷️ Categories  │                                                     │
│  🎁 Coupons     │                                                     │
│  📈 Analytics   │                                                     │
│  ⚙️ Settings    │                                                     │
└─────────────────┴───────────────────────────────────────────────────┘
```

### Admin Screen A: Dashboard
```
STATS ROW:
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Today Orders │  │Today Revenue │  │ Active Users │  │ Low Stock    │
│    147       │  │  ₹24,560     │  │    892       │  │   12 items   │
│ ▲ 12% ↑    │  │ ▲ 8% ↑      │  │ ▲ 5% ↑      │  │ ⚠️ Warning   │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘

ORDERS CHART (Line chart — revenue last 7 days)
RECENT ORDERS TABLE (orderId, customer, amount, status, action)
```

### Admin Screen B: Product Management
```
[+ Add Product]  [Search...]  [Filter by Category ▼]  [Status ▼]

TABLE:
┌─────────┬──────────────────┬──────────┬──────────┬────────┬─────────┐
│ Image   │ Name             │ Category │ Price    │ Stock  │ Actions │
├─────────┼──────────────────┼──────────┼──────────┼────────┼─────────┤
│ [img]   │ Amul Butter 500g │ Dairy    │ ₹52      │ 142    │ ✏️ 🗑️  │
│ [img]   │ Brown Bread 400g │ Bakery   │ ₹35      │  0 ⚠️  │ ✏️ 🗑️  │
└─────────┴──────────────────┴──────────┴──────────┴────────┴─────────┘
```

### Admin Screen C: Add/Edit Product Modal
```
┌──────────────────────────────────────────────────────────────────────┐
│  Add New Product                                          [× Close]  │
├──────────────────────────────────────────────────────────────────────┤
│  Product Images:  [Upload Images (drag & drop)]                      │
│  Product Name:    [Amul Full Cream Milk                           ]  │
│  Category:        [Dairy ▼]     Brand:   [Amul               ]      │
│  Unit/Weight:     [500ml     ]                                       │
│  MRP:             [₹ 30.00  ]  Selling Price: [₹ 28.00         ]    │
│  Stock Qty:       [500       ]  Low Stock At:  [50              ]    │
│  Description:     [Long text area...                             ]   │
│  Tags:            [milk, dairy, amul  ×]  [+ Add tag]               │
│  Availability:    ● Available  ○ Unavailable                         │
│  Featured:        ☐ Mark as featured product                         │
├──────────────────────────────────────────────────────────────────────┤
│  [Cancel]                                        [Save Product]       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 10. AUTH FLOW

```
CUSTOMER REGISTRATION:
1. POST /auth/signup {email, password, name, phone}
2. auth-service creates user with verified=false, roles=["CUSTOMER"]
3. Publishes user.registered to Kafka
4. notification-service sends OTP email (6-digit code, valid 5 min)
5. User enters OTP → GET /auth/verify?otp=123456
6. auth-service validates OTP from Redis → marks verified=true
7. Returns {accessToken (15 min), refreshToken (30 days)}

LOGIN:
1. POST /auth/login {email, password}
2. auth-service validates credentials + checks verified=true
3. Returns {accessToken (15 min), refreshToken (30 days), user info}
4. Frontend stores accessToken in memory (Zustand), refreshToken in httpOnly cookie

PASSWORD RESET VIA EMAIL LINK:
1. POST /auth/forgot-password {email}
2. auth-service generates UUID token (e.g. "a1b2c3d4-5678-...")
3. Stores in Redis: pwd_reset:{token} = userId  (TTL: 15 min)
4. Publishes user.password.reset to Kafka
5. notification-service sends email:
   "Reset your password → https://app.com/reset-password/a1b2c3d4-5678"
6. User clicks link → browser opens /reset-password/a1b2c3d4-5678
7. Frontend calls GET /auth/reset-password/validate/a1b2c3d4-5678
   → 200 OK (valid) or 410 GONE (expired)
8. User enters new password → POST /auth/reset-password/a1b2c3d4-5678 {newPassword}
9. auth-service looks up Redis → finds userId → updates password → deletes token
10. Frontend redirects to login with success toast

PROTECTED API CALL:
1. Frontend sends Bearer token in Authorization header
2. API Gateway JwtFilter validates token signature + expiry
3. Gateway injects X-User-Id and X-User-Role headers into downstream request
4. Downstream services trust these headers (no JWT lib needed in each service)

TOKEN REFRESH:
1. Access token expires → 401 response from Gateway
2. Frontend Axios interceptor catches 401 → calls POST /auth/refresh (auto)
3. auth-service validates refresh token from Redis → returns new accessToken
4. Axios retries original request with new token

LOGOUT:
1. POST /auth/logout
2. auth-service deletes refresh token from Redis
3. Adds access token to blacklist in Redis (TTL = remaining token expiry)
4. Frontend clears accessToken from Zustand store

ADMIN ACCESS:
1. Admin user has roles=["ADMIN"]
2. API Gateway checks X-User-Role=ADMIN for /api/admin/** routes
3. Rejects non-admins with 403 Forbidden
```

---

## 11. DEVELOPMENT ROADMAP

> Each stage produces a **runnable, testable system**. Never more than one stage away from something working.

### Stage Overview

| Stage | Focus | Services | Est. Weeks |
|---|---|---|---|
| 1 | Infrastructure & Skeleton | eureka-server, config-server, api-gateway | 1–2 |
| 2 | Auth & User + Audit Foundation | auth-service, user-service, notification-service, audit-service | 2–3 |
| 3 | Product Catalog & Inventory | product-service, inventory-service | 2 |
| 4 | Cart & Coupons | cart-service, coupon-service | 1–2 |
| 5 | Orders & Payments | order-service, payment-service | 2–3 |
| 6 | Frontend | customer-app, admin-panel | 4–5 |
| 7 | Delivery, Reviews & Docker | delivery-service, review-service, full Docker stack | 2–3 |

---

### Stage 1 — Infrastructure & Skeleton

**Goal:** Everything boots, services register with Eureka, gateway routes requests.

**Services to build:**
- `eureka-server` (port 8761) — service discovery; dashboard accessible at `localhost:8761`
- `config-server` (port 8888) — centralised config; reads from `backend/config-server/src/main/resources/configs/`
- `api-gateway` (port 8080) — routes `/api/**`, JWT filter stubbed (pass-through for now), Redis rate limiting wired
- `docker-compose.infra.yml` — Zookeeper + Kafka + Redis + Kafka UI + Redis Commander

**Checklist:**
- [ ] Create Maven multi-module parent POM (`backend/pom.xml`) with all service modules declared
- [ ] `eureka-server` — `spring-cloud-starter-netflix-eureka-server`, `@EnableEurekaServer`
- [ ] `config-server` — `spring-cloud-config-server`, `@EnableConfigServer`, file-based config
- [ ] `api-gateway` — Spring Cloud Gateway routes for all 12 services, Redis rate limiter, stub JWT filter
- [ ] All services register with Eureka (`eureka.client.serviceUrl.defaultZone`)
- [ ] `docker-compose.infra.yml` — Zookeeper, Kafka, Redis (with password), Kafka UI (:9093), Redis Commander (:8081)
- [ ] Verify: Eureka dashboard shows all registered services
- [ ] Verify: `GET localhost:8080/actuator/health` returns 200 via gateway

**✅ Done when:** All 3 Spring Boot apps start and appear as UP in Eureka UI at `localhost:8761`.

---

### Stage 2 — Auth & User

**Goal:** Full auth flow end-to-end — signup → OTP verify → login → JWT → password reset link.

**Services to build:**
- `auth-service` (port 8081) — reuse journalApp `JwtFilter.java`, `JwtUtil.java`, `SpringSecurity.java`
- `user-service` (port 8082) — profile + addresses CRUD
- `notification-service` (port 8089) — Kafka consumer for email sending (SMTP)

**Checklist:**
- [ ] **auth-service**
  - [ ] `POST /auth/signup` — creates `auth_db.users`, generates UUID userId, publishes `user.registered` to Kafka
  - [ ] `GET /auth/verify?otp=` — validates OTP from Redis (`otp:{email}`, 5 min TTL), sets `isVerified=true`
  - [ ] `POST /auth/login` — validates credentials + `isVerified`, returns `accessToken` (15 min) + `refreshToken` (30 days in Redis `refresh:{userId}`)
  - [ ] `POST /auth/refresh` — validates refresh token from Redis, returns new accessToken
  - [ ] `POST /auth/logout` — deletes refresh token, blacklists access token in Redis (`blacklist:{token}`)
  - [ ] `POST /auth/forgot-password` — generates UUID token → Redis `pwd_reset:{token}` (15 min TTL), publishes `user.password.reset` to Kafka
  - [ ] `GET /auth/reset-password/validate/{token}` — checks Redis → 200 OK or 410 GONE
  - [ ] `POST /auth/reset-password/{token}` — validates token, updates password, deletes token
  - [ ] JWT claims: `{sub, userId, role, email, iat, exp}`
- [ ] **notification-service**
  - [ ] Kafka consumer: `user.registered` → send welcome + OTP email via SMTP (reuse journalApp email pattern)
  - [ ] Kafka consumer: `user.password.reset` → send password reset link email (`PASSWORD_RESET_BASE_URL/{token}`)
  - [ ] Log all notifications to `notification_db.notification_logs`
- [ ] **user-service**
  - [ ] `GET/PUT /users/profile` — read/update `user_db.user_profiles`
  - [ ] `POST/GET /users/addresses` — add and list addresses
  - [ ] `PUT /users/addresses/{id}` — update address
  - [ ] `DELETE /users/addresses/{id}` — delete address
  - [ ] `PUT /users/addresses/{id}/default` — set default address
- [ ] Enable JWT validation in api-gateway global filter — extract `userId` + `role`, inject `X-User-Id` / `X-User-Role` headers into downstream requests
- [ ] Add `X-Correlation-Id` injection to api-gateway (generate UUID if not present, forward to all downstream services)
- [ ] Swagger UI working for auth-service and user-service
- [ ] `GlobalExceptionHandler` (`@RestControllerAdvice`) in each service
- [ ] **audit-service**
  - [ ] Create `audit-service` Maven module (port 8092), register with Eureka, pull config from config-server
  - [ ] `AuditEventLog` MongoDB document (`audit_db.event_logs`) with all fields from Entity 18 schema
  - [ ] Spy Kafka consumer — subscribe to `user.registered` and `user.password.reset` topics under `audit-consumer-group`; persist each message as an `AuditEventLog` with `eventCategory=KAFKA`
  - [ ] `audit.events` Kafka consumer — receives explicit audit records from other services (notification outcomes, Feign call results, payment webhook details)
  - [ ] `GET /audit/events` — paginated query with filters: `sourceService`, `eventCategory`, `status`, `kafkaTopic`, `from`, `to`, `userId` (ADMIN only)
  - [ ] `GET /audit/trace/{correlationId}` — returns all audit records with the given correlationId, ordered by `publishedAt` (ADMIN only)
  - [ ] `GET /audit/entity/{entityType}/{entityId}` — all events for ORDER / PAYMENT / USER (ADMIN only)
  - [ ] `GET /audit/failed` — all records with `status=FAILED` (ADMIN only)
  - [ ] All 7 MongoDB indexes created (correlationId, eventType, entityId+entityType, status, userId, kafkaTopic, sourceService+targetService)
  - [ ] **notification-service** update: after each SMTP/SMS/FCM attempt, publish result to `audit.events` (`eventCategory=NOTIFICATION`, `status=SUCCESS/FAILED`, `errorMessage` on failure)
  - [ ] **auth-service** update: forward `X-Correlation-Id` from incoming HTTP header into Kafka message headers when publishing `user.registered` and `user.password.reset`

**Kafka topics active after this stage:** `user.registered`, `user.password.reset`, `audit.events`

**✅ Done when:** Full flow works via Postman: signup → OTP email arrives → verify → login → call protected `/users/profile` with JWT → forgot password → reset link email arrives → reset password → login with new password. AND: `GET /audit/trace/{correlationId}` returns the full chain of events for a signup flow — Kafka message captured + notification delivery outcome recorded.

---

### Stage 3 — Product Catalog & Inventory

**Goal:** Admin can create products with images; customers can browse, filter, search, and see stock.

**Services to build:**
- `product-service` (port 8083)
- `inventory-service` (port 8084)

**Checklist:**
- [ ] **product-service**
  - [ ] `POST /admin/categories` + `GET /categories` — category tree with parent/child hierarchy
  - [ ] `POST /admin/products` — create product, upload images to Cloudinary, store URLs in `product_db.products`
  - [ ] `PUT /admin/products/{id}` — update product
  - [ ] `DELETE /admin/products/{id}` + `PUT /admin/products/{id}/toggle` — delete / toggle availability
  - [ ] `GET /products` — paginated list with filters (category, price range, availability) + sort (price, rating)
  - [ ] `GET /products/{id}` — product detail
  - [ ] `GET /products/search?q=` — MongoDB full-text search on `{name, tags, brand}` text index
  - [ ] `GET /products/category/{slug}` — products by category
  - [ ] Redis cache: `products:category:{slug}` (10 min), `products:featured` (30 min)
  - [ ] Kafka consumer: `inventory.out` → set `isAvailable=false` on product
  - [ ] MongoDB text index: `{name: "text", tags: "text", brandName: "text"}`
- [ ] **inventory-service**
  - [ ] On product creation: receive `productId` and create stock document in `inventory_db.stock` (qty = 0)
  - [ ] `GET /inventory/{productId}` — current stock
  - [ ] `POST /inventory/reserve` — reserve qty for an order (optimistic locking, `availableQty` must not go < 0)
  - [ ] `POST /inventory/release` — release reserved qty on cancellation
  - [ ] `POST /inventory/confirm` — deduct reserved qty after payment confirmed
  - [ ] `GET/PUT /admin/inventory` — view all stock, update qty (creates StockMovement record)
  - [ ] Kafka publish: `inventory.low` when `availableQty ≤ lowStockThreshold`
  - [ ] Kafka publish: `inventory.out` when `availableQty = 0`
  - [ ] notification-service consumes `inventory.low` → email alert to admin
- [ ] MongoDB indexes for products (text, category.slug, sellingPrice) and stock (productId unique)

**Kafka topics active after this stage:** `+ inventory.low`, `inventory.out`

**✅ Done when:** Admin creates a product with images via Postman; customer searches products; stock update triggers Kafka event; notification-service emails admin on low stock.

---

### Stage 4 — Cart & Coupons

**Goal:** Customer builds a cart, applies a discount coupon, sees correct price breakdown.

**Services to build:**
- `coupon-service` (port 8090)
- `cart-service` (port 8087)

**Checklist:**
- [ ] **coupon-service**
  - [ ] `POST /admin/coupons` — create coupon (FLAT, PERCENT, FIRST_ORDER, FREE_DELIVERY types)
  - [ ] `GET/PUT/DELETE /admin/coupons/{id}` — manage coupons
  - [ ] `GET /admin/coupons/{id}/usage` — usage stats
  - [ ] `POST /coupons/validate` — accepts `{code, userId, cartTotal}`:
    - checks `isActive`, `validFrom ≤ now ≤ validUntil`
    - checks `cartTotal ≥ minOrderAmount`
    - checks `usedCount < totalUsageLimit` (if set)
    - checks per-user usage via `coupon_db.coupon_usage`
    - checks `applicableOnFirstOrderOnly` by calling order-service (Feign) later; skip for now
    - returns `{valid: true, discount: 18.0, message: "10% off applied"}`
  - [ ] Redis cache: `coupon:{code}` (5 min TTL) for fast repeated lookups
- [ ] **cart-service**
  - [ ] `POST /cart/items` — add item; calls product-service (Feign) to verify price + `isAvailable`
  - [ ] `PUT /cart/items/{productId}` — update quantity (0 = remove)
  - [ ] `DELETE /cart/items/{productId}` — remove item
  - [ ] `DELETE /cart` — clear entire cart
  - [ ] `GET /cart` — returns full cart with:
    - all items (productId, name, qty, unitPrice, totalPrice)
    - `itemsTotal`, `deliveryFee` (0 if total ≥ threshold), `couponDiscount`, `totalAmount`
  - [ ] `POST /cart/promo` — apply promo code; calls coupon-service (Feign) to validate; stores code in `cart:{userId}:promo`
  - [ ] `DELETE /cart/promo` — remove promo code
  - [ ] Cart stored as Redis Hash: `cart:{userId}` with 7-day TTL
  - [ ] Kafka consumer: `order.confirmed` → `DEL cart:{userId}` (clear cart after order placed)
  - [ ] Resilience4j circuit breaker on Feign calls to product-service and coupon-service

**Feign clients added:**
- `cart-service → product-service` (price + availability check)
- `cart-service → coupon-service` (validate promo)

**✅ Done when:** Customer adds items to cart, applies "SAVE10" coupon, gets correct discounted total, cart persists in Redis for 7 days.

---

### Stage 5 — Orders & Payments

**Goal:** Complete purchase — cart → order → Razorpay payment → order confirmed → stock deducted → notifications sent.

**Services to build:**
- `order-service` (port 8085)
- `payment-service` (port 8086)

**Checklist:**
- [ ] **order-service**
  - [ ] `POST /orders` — create order from cart:
    1. Fetch cart from cart-service (Feign)
    2. Validate coupon with coupon-service (Feign) if promo applied
    3. Reserve stock with inventory-service (Feign) — fail fast if stock insufficient
    4. Snapshot prices into `order_db.orders` (items[].mrp, items[].unitPrice, deliveryAddress)
    5. Set `status=PAYMENT_PENDING`, publish `order.created`
  - [ ] `GET /orders` — my orders list (paginated, filter by status)
  - [ ] `GET /orders/{id}` — order detail with statusHistory
  - [ ] `POST /orders/{id}/cancel` — cancel if status ≤ PACKED; publish `order.cancelled`
  - [ ] `GET/PUT /admin/orders` — all orders, update status → publish `order.status.updated`
  - [ ] Kafka consumer: `payment.success` → set `status=CONFIRMED`, publish `order.confirmed`
  - [ ] Kafka consumer: `payment.failed` → set `status=PAYMENT_FAILED`
  - [ ] Order number generation: `BLK-YYYYMMDD-{sequence}`
- [ ] **payment-service**
  - [ ] `POST /payments/initiate` — creates Razorpay order via SDK; stores transaction in `payment_db.transactions` with `status=INITIATED`; returns `{razorpayOrderId, amount, key}`
  - [ ] `POST /payments/verify` — called from frontend after Razorpay UI closes; verifies signature manually (fallback to webhook)
  - [ ] `POST /payments/webhook` — Razorpay webhook; verify HMAC signature; publish `payment.success` or `payment.failed`
  - [ ] `GET /payments/history` — transaction history for current user
  - [ ] `POST /payments/refund/{txnId}` — initiate Razorpay refund; record in `payment_db.refunds`
  - [ ] Kafka consumer: `order.cancelled` → trigger refund if `paymentStatus=PAID`
- [ ] **Wire all Kafka flows:**
  - `order.created` → notification-service (order received email)
  - `order.confirmed` → inventory-service (confirm stock deduction) + notification-service (confirmed email/SMS) + delivery-service (create DeliveryTask — stub for now) + cart-service (clear cart)
  - `order.cancelled` → inventory-service (release stock) + payment-service (refund) + notification-service
  - `order.status.updated` → notification-service (SMS/push per status)
  - `payment.success` → order-service (confirm) + notification-service (receipt email)
  - `payment.failed` → order-service (mark failed) + notification-service
- [ ] Record CouponUsage in coupon-service when order is confirmed (call via Feign from order-service)
- [ ] MongoDB indexes: `orders.userId+createdAt`, `orders.orderNumber (unique)`, `transactions.orderId (unique)`

**Feign clients added:**
- `order-service → inventory-service` (reserve/release/confirm stock)
- `order-service → coupon-service` (validate coupon + record usage)
- `order-service → cart-service` (fetch cart contents)

**✅ Done when:** Full purchase works via Postman: create order → initiate payment → simulate Razorpay webhook → order status = CONFIRMED → stock decremented in inventory → cart cleared → confirmation email received.

---

### Stage 6 — Frontend

**Goal:** Both UIs fully functional against real backend APIs.

#### 6a — Customer App (`frontend/customer-app`)

**Stack:** React 18 + Vite + React Router v6 + Zustand + Tailwind CSS + Axios

**Checklist:**
- [ ] Project scaffold: `npm create vite@latest`, Tailwind config, React Router setup, Zustand stores (`authStore`, `cartStore`)
- [ ] Axios instance with base URL `http://localhost:8080/api`; interceptor: on 401 → call `/auth/refresh` → retry original request
- [ ] **Auth pages**
  - [ ] Login — email + password, show/hide toggle, "Forgot Password?" link
  - [ ] Signup — name, email, phone, password; on success → redirect to OTP verify
  - [ ] OTP Verify — 6-digit input, resend button (60s cooldown)
  - [ ] Forgot Password — email input, success state "Check your inbox"
  - [ ] Reset Password — reads token from URL params, new password + confirm, submit → redirect to login
- [ ] **Home page**
  - [ ] Location bar at top (city + pincode)
  - [ ] Search bar → navigates to search results
  - [ ] Category horizontal scroll
  - [ ] Featured / Best Sellers product grid
  - [ ] Floating cart bar (shows item count + total, sticky at bottom)
- [ ] **Product Listing** — category page, search results page; filter sidebar (price, brand, availability); sort dropdown; infinite scroll / pagination; out-of-stock overlay on card
- [ ] **Product Detail** — image carousel, name/price/MRP/discount%, unit, description, add-to-cart with quantity stepper, avg rating display
- [ ] **Cart page** — item list with quantity controls, promo code input (calls `/cart/promo`), bill summary breakdown (items, delivery fee, coupon discount, total)
- [ ] **Checkout — Address** — list saved addresses with radio select, "Add New Address" form (all Entity 3 fields), map pin (optional)
- [ ] **Checkout — Payment** — payment method selector (UPI / Card / Net Banking / COD), UPI ID input if UPI, Razorpay SDK integration for Card/UPI, "PAY ₹X SECURELY" button
- [ ] **Order Success + Tracking** — order number, status timeline, live map (WebSocket → update rider marker every 5s), delivery partner card, cancel button (only before OUT_FOR_DELIVERY)
- [ ] **My Orders** — tabs (All / Active / Delivered / Cancelled); order cards with status badge; "Track Order" / "Rate Order" / "Reorder" action buttons
- [ ] **User Profile** — display name/email/phone, edit profile form, menu list (Orders, Addresses, Payment Methods, Coupons, Logout)

#### 6b — Admin Panel (`frontend/admin-panel`)

**Stack:** React 18 + Vite + Tailwind CSS + Axios (no Zustand needed — simpler state)

**Checklist:**
- [ ] Admin login page (separate from customer app)
- [ ] Sidebar layout with active route highlight
- [ ] **Dashboard** — stats cards (Today Orders, Revenue, Active Users, Low Stock), revenue line chart (last 7 days), recent orders table
- [ ] **Products** — data table with search + category filter; Add/Edit product modal (all fields, image upload to Cloudinary via product-service); delete + toggle availability
- [ ] **Categories** — tree view, add/edit category modal with parent selector + image upload
- [ ] **Inventory** — stock table with color-coded level bars (green/amber/red); inline qty edit; stock movement history per product
- [ ] **Orders** — table with status filter; order detail modal (items, address, timeline); status updater dropdown
- [ ] **Coupons** — table with active/inactive toggle; create coupon modal (all fields including type, value, limits, dates)
- [ ] **Customers** — read-only user list with search
- [ ] **Reviews** — table with product filter; hide/show toggle per review

**✅ Done when:** Full customer purchase journey (signup → browse → add to cart → checkout → pay → track order) works in browser; admin can add a product, update stock, and change an order status.

---

### Stage 7 — Delivery, Reviews & Docker

**Goal:** Live delivery tracking works; review system active; entire stack runs in Docker.

**Services to build:**
- `delivery-service` (port 8088)
- `review-service` (port 8091)

**Checklist:**
- [ ] **delivery-service**
  - [ ] Kafka consumer: `order.confirmed` → create `delivery_db.delivery_tasks` with `status=UNASSIGNED`
  - [ ] `POST /delivery/assign` (admin) — assign delivery partner to task, set `status=ASSIGNED`
  - [ ] `PUT /delivery/{taskId}/status` (delivery partner) — update status (PICKED_UP → OUT_FOR_DELIVERY → DELIVERED); on DELIVERED publish `order.delivered` Kafka event
  - [ ] `PUT /delivery/{taskId}/location` — update `currentLat`, `currentLng` on DeliveryTask
  - [ ] WebSocket endpoint: `ws://localhost:8080/ws/delivery/track/{orderId}` — push `{lat, lng, status, estimatedMinutes}` every 5 seconds to subscribed frontend clients
  - [ ] `GET /delivery/track/{orderId}` — REST fallback for current location
  - [ ] `GET /delivery/slots` — available delivery time slots
- [ ] **review-service**
  - [ ] Kafka consumer: `order.delivered` → set `Order.isReviewPending=true` (via Feign to order-service)
  - [ ] `POST /reviews` — submit review; validates orderId belongs to userId; validates order status = DELIVERED; one review per `{orderId + userId}` (unique index)
  - [ ] `GET /reviews/product/{id}` — paginated reviews for a product
  - [ ] `GET /reviews/my` — current user's reviews
  - [ ] `DELETE /admin/reviews/{id}` — admin moderation (sets `isVisible=false`)
  - [ ] Calls product-service (Feign) after each new review to recalculate and update `avgRating` + `reviewCount`
  - [ ] Wire customer-app: post-delivery review screen, rating display on product cards
- [ ] **Notification-service additions**
  - [ ] Kafka consumer: `order.delivered` → "Rate your experience" email with review link
  - [ ] Firebase FCM push notifications for `order.out_for_delivery` and `order.delivered`
- [ ] **Docker — full stack**
  - [ ] `Dockerfile` in every backend service folder (multi-stage Maven → JRE alpine)
  - [ ] `Dockerfile` in `frontend/customer-app` and `frontend/admin-panel` (Node build → Nginx alpine)
  - [ ] `nginx.conf` for each frontend (SPA fallback, static asset caching)
  - [ ] Finalize `docker-compose.yml` — all 14 services + infra with health checks and `depends_on`
  - [ ] `.env` file with all variables (never commit real secrets)
  - [ ] `.dockerignore` in each service folder
  - [ ] Test: `docker compose up -d` — all containers reach healthy state
  - [ ] Test: full purchase + live delivery tracking works inside Docker
  - [ ] `docker-compose.prod.yml` — `restart: always`, resource limits, internal services remove host port bindings

**Feign clients added:**
- `review-service → product-service` (update avgRating + reviewCount)
- `review-service → order-service` (verify order belongs to user and is DELIVERED)
- `delivery-service → user-service` (get customer address details)

**Kafka topics active after this stage:** `+ order.delivered`

**✅ Done when:** `docker compose up -d` starts all containers; customer completes a purchase, watches live rider tracking on map, receives delivery notification, submits a review that updates the product's star rating.

---

## PROJECT FOLDER STRUCTURE

```
blinkit-clone/
├── backend/
│   ├── eureka-server/            ← Service discovery (port 8761)
│   ├── config-server/            ← Centralised config (port 8888)
│   ├── api-gateway/              ← Spring Cloud Gateway (port 8080)
│   ├── auth-service/             ← JWT auth, OTP, password reset link (port 8081)
│   ├── user-service/             ← User profiles & addresses (port 8082)
│   ├── product-service/          ← Product catalog + search (port 8083)
│   ├── inventory-service/        ← Stock management (port 8084)
│   ├── order-service/            ← Order lifecycle state machine (port 8085)
│   ├── payment-service/          ← Razorpay integration (port 8086)
│   ├── cart-service/             ← Redis cart + coupon apply (port 8087)
│   ├── delivery-service/         ← Delivery tracking + WebSocket (port 8088)
│   ├── notification-service/     ← Kafka consumer: email/SMS/push (port 8089)
│   ├── coupon-service/           ← Coupon CRUD + validation (port 8090)
│   ├── review-service/           ← Product ratings & reviews (port 8091)
│   └── pom.xml                   ← Parent POM (multi-module Maven)
├── frontend/
│   ├── customer-app/             ← React 18 + Vite + React Router + Zustand + Tailwind
│   │   ├── src/
│   │   │   ├── pages/            ← Home, Login, Signup, Cart, Orders, Profile...
│   │   │   ├── components/       ← ProductCard, CartBar, AddressModal...
│   │   │   ├── store/            ← Zustand: authStore, cartStore
│   │   │   ├── api/              ← Axios instances + interceptors
│   │   │   └── hooks/            ← useCart, useAuth, useWebSocket
│   └── admin-panel/              ← React 18 + Vite (admin dashboard)
├── docker-compose.yml            ← All services + MongoDB + Redis + Kafka + Zookeeper
└── README.md
```

---

## 12. DOCKER — CONTAINERISATION & DEPLOYMENT

---

### 12.1 Folder Structure for Docker Files

```
blinkit-clone/
├── backend/
│   ├── api-gateway/
│   │   └── Dockerfile
│   ├── auth-service/
│   │   └── Dockerfile
│   ├── user-service/
│   │   └── Dockerfile
│   ├── product-service/
│   │   └── Dockerfile
│   ├── ... (same Dockerfile pattern for all services)
├── frontend/
│   ├── customer-app/
│   │   └── Dockerfile
│   └── admin-panel/
│       └── Dockerfile
├── docker-compose.yml          ← Full local dev stack
├── docker-compose.prod.yml     ← Production overrides
├── docker-compose.infra.yml    ← Infrastructure only (MongoDB, Redis, Kafka)
└── .env                        ← All environment variables (never commit secrets)
```

---

### 12.2 Dockerfile — Spring Boot Services (All backends use same pattern)

**Multi-stage build** — build layer compiles the JAR, runtime layer only contains JRE.
This keeps the final image small (~200MB vs ~600MB with JDK).

```dockerfile
# ─── Stage 1: Build ───────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml first — lets Docker cache the dependency download layer
# Only re-downloads dependencies when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build JAR (skip tests — tests run in CI pipeline)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create non-root user for security (never run as root in containers)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the compiled JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the service port (matches application.yml server.port)
EXPOSE 8081

# JVM tuning for containers:
# -XX:+UseContainerSupport      → respects Docker memory limits
# -XX:MaxRAMPercentage=75.0     → uses 75% of container RAM for heap
# -Djava.security.egd=...       → faster startup (avoids /dev/random blocking)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

> Save this as `Dockerfile` inside **each** backend service folder.
> Change `EXPOSE` port per service: 8081 (auth), 8082 (user), 8083 (product), etc.

---

### 12.3 Dockerfile — React Frontend (customer-app & admin-panel)

**Multi-stage build** — Node builds the static files, Nginx serves them.
Final image is ~25MB.

```dockerfile
# ─── Stage 1: Build React app ─────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files first for dependency caching
COPY package.json package-lock.json ./
RUN npm ci --silent

# Copy source and build
COPY . .
RUN npm run build
# Output: /app/dist

# ─── Stage 2: Serve with Nginx ────────────────────────────────────
FROM nginx:alpine AS runtime

# Remove default Nginx page
RUN rm -rf /usr/share/nginx/html/*

# Copy built React app
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom Nginx config (handles React Router — all routes → index.html)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

**`nginx.conf`** (place in `frontend/customer-app/nginx.conf`):
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # All routes served by React Router (SPA fallback)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets for 1 year
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Disable caching for index.html (always latest version)
    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }
}
```

---

### 12.4 .env File — All Environment Variables

```
# ─── MongoDB Atlas ────────────────────────────────────────────────
MONGO_URI=mongodb+srv://<user>:<pass>@cluster.mongodb.net

# ─── Redis ────────────────────────────────────────────────────────
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=yourredispassword

# ─── Kafka ────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# ─── JWT ──────────────────────────────────────────────────────────
JWT_SECRET_KEY=your_256bit_secret_key_here_min_32_chars
JWT_EXPIRY_TIME_IN_MINUTE=15
JWT_REFRESH_EXPIRY_DAYS=30

# ─── Service Ports ────────────────────────────────────────────────
EUREKA_PORT=8761
CONFIG_SERVER_PORT=8888
GATEWAY_PORT=8080
AUTH_SERVICE_PORT=8081
USER_SERVICE_PORT=8082
PRODUCT_SERVICE_PORT=8083
INVENTORY_SERVICE_PORT=8084
ORDER_SERVICE_PORT=8085
PAYMENT_SERVICE_PORT=8086
CART_SERVICE_PORT=8087
DELIVERY_SERVICE_PORT=8088
NOTIFICATION_SERVICE_PORT=8089
COUPON_SERVICE_PORT=8090
REVIEW_SERVICE_PORT=8091

# ─── Eureka ───────────────────────────────────────────────────────
EUREKA_SERVER_URL=http://eureka-server:8761/eureka

# ─── Config Server ────────────────────────────────────────────────
CONFIG_SERVER_URL=http://config-server:8888

# ─── Email (SMTP) ─────────────────────────────────────────────────
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your@gmail.com
SMTP_PASSWORD=your_app_password

# ─── Twilio (SMS) ─────────────────────────────────────────────────
TWILIO_ACCOUNT_SID=ACxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxx
TWILIO_PHONE_NUMBER=+1xxxxxxxxxx

# ─── Razorpay (Payments) ──────────────────────────────────────────
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxx
RAZORPAY_KEY_SECRET=xxxxxxxxxx
RAZORPAY_WEBHOOK_SECRET=xxxxxxxxxx

# ─── Cloudinary (Image Storage) ───────────────────────────────────
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=xxxxxxxxxx
CLOUDINARY_API_SECRET=xxxxxxxxxx

# ─── Firebase FCM (Push Notifications) ───────────────────────────
FIREBASE_PROJECT_ID=your_project_id
FIREBASE_CREDENTIALS_PATH=/app/config/firebase-credentials.json

# ─── Frontend URLs ────────────────────────────────────────────────
FRONTEND_CUSTOMER_URL=http://localhost:3000
FRONTEND_ADMIN_URL=http://localhost:3001
PASSWORD_RESET_BASE_URL=http://localhost:3000/reset-password
```

> **IMPORTANT:** Add `.env` to `.gitignore`. Never commit real secrets to git.
> For production, use Docker secrets or a secrets manager (AWS Secrets Manager / HashiCorp Vault).

---

### 12.5 docker-compose.yml — Complete Local Dev Stack

```yaml
version: '3.9'

# ─── Shared network — all services communicate by container name ──
networks:
  blinkit-network:
    driver: bridge

# ─── Named volumes — data survives container restarts ─────────────
volumes:
  mongo-data:
  redis-data:
  kafka-data:
  zookeeper-data:

services:

  # ════════════════════════════════════════════════════
  # INFRASTRUCTURE LAYER  (start first)
  # ════════════════════════════════════════════════════

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    container_name: blinkit-zookeeper
    networks: [blinkit-network]
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
    healthcheck:
      test: ["CMD", "nc", "-z", "localhost", "2181"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: blinkit-kafka
    networks: [blinkit-network]
    ports:
      - "9092:9092"
    depends_on:
      zookeeper:
        condition: service_healthy
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      # PLAINTEXT_HOST → accessible from your host machine (localhost:9092)
      # PLAINTEXT       → accessible inside Docker network (kafka:29092)
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    volumes:
      - kafka-data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 15s
      timeout: 10s
      retries: 10

  redis:
    image: redis:7-alpine
    container_name: blinkit-redis
    networks: [blinkit-network]
    ports:
      - "6379:6379"
    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD}
      --appendonly yes
      --maxmemory 256mb
      --maxmemory-policy allkeys-lru
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # NOTE: MongoDB Atlas is used as cloud DB (no local mongo needed).
  # For local-only dev without Atlas, uncomment below:
  #
  # mongodb:
  #   image: mongo:7
  #   container_name: blinkit-mongo
  #   networks: [blinkit-network]
  #   ports:
  #     - "27017:27017"
  #   environment:
  #     MONGO_INITDB_ROOT_USERNAME: root
  #     MONGO_INITDB_ROOT_PASSWORD: password
  #   volumes:
  #     - mongo-data:/data/db
  #   healthcheck:
  #     test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
  #     interval: 10s
  #     timeout: 5s
  #     retries: 5

  # ════════════════════════════════════════════════════
  # PLATFORM LAYER  (start after infrastructure)
  # ════════════════════════════════════════════════════

  eureka-server:
    build:
      context: ./backend/eureka-server
      dockerfile: Dockerfile
    container_name: blinkit-eureka
    networks: [blinkit-network]
    ports:
      - "${EUREKA_PORT}:8761"
    environment:
      SERVER_PORT: 8761
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8761/actuator/health"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 30s

  config-server:
    build:
      context: ./backend/config-server
      dockerfile: Dockerfile
    container_name: blinkit-config
    networks: [blinkit-network]
    ports:
      - "${CONFIG_SERVER_PORT}:8888"
    depends_on:
      eureka-server:
        condition: service_healthy
    environment:
      SERVER_PORT: 8888
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8888/actuator/health"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 30s

  # ════════════════════════════════════════════════════
  # APPLICATION LAYER  (start after platform)
  # ════════════════════════════════════════════════════

  api-gateway:
    build:
      context: ./backend/api-gateway
      dockerfile: Dockerfile
    container_name: blinkit-gateway
    networks: [blinkit-network]
    ports:
      - "${GATEWAY_PORT}:8080"
    depends_on:
      eureka-server:
        condition: service_healthy
      config-server:
        condition: service_healthy
    environment:
      SERVER_PORT: 8080
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      CONFIG_SERVER_URL: ${CONFIG_SERVER_URL}
      JWT_SECRET_KEY: ${JWT_SECRET_KEY}
      REDIS_HOST: redis
      REDIS_PORT: ${REDIS_PORT}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 40s

  auth-service:
    build:
      context: ./backend/auth-service
      dockerfile: Dockerfile
    container_name: blinkit-auth
    networks: [blinkit-network]
    ports:
      - "${AUTH_SERVICE_PORT}:8081"
    depends_on:
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SERVER_PORT: 8081
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/auth_db
      REDIS_HOST: redis
      REDIS_PORT: ${REDIS_PORT}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      JWT_SECRET_KEY: ${JWT_SECRET_KEY}
      JWT_EXPIRY_TIME_IN_MINUTE: ${JWT_EXPIRY_TIME_IN_MINUTE}
      JWT_REFRESH_EXPIRY_DAYS: ${JWT_REFRESH_EXPIRY_DAYS}
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}
      SMTP_HOST: ${SMTP_HOST}
      SMTP_PORT: ${SMTP_PORT}
      SMTP_USERNAME: ${SMTP_USERNAME}
      SMTP_PASSWORD: ${SMTP_PASSWORD}
      PASSWORD_RESET_BASE_URL: ${PASSWORD_RESET_BASE_URL}

  user-service:
    build:
      context: ./backend/user-service
      dockerfile: Dockerfile
    container_name: blinkit-user
    networks: [blinkit-network]
    ports:
      - "${USER_SERVICE_PORT}:8082"
    depends_on:
      eureka-server:
        condition: service_healthy
    environment:
      SERVER_PORT: 8082
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/user_db

  product-service:
    build:
      context: ./backend/product-service
      dockerfile: Dockerfile
    container_name: blinkit-product
    networks: [blinkit-network]
    ports:
      - "${PRODUCT_SERVICE_PORT}:8083"
    depends_on:
      eureka-server:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SERVER_PORT: 8083
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/product_db
      REDIS_HOST: redis
      REDIS_PORT: ${REDIS_PORT}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}
      CLOUDINARY_CLOUD_NAME: ${CLOUDINARY_CLOUD_NAME}
      CLOUDINARY_API_KEY: ${CLOUDINARY_API_KEY}
      CLOUDINARY_API_SECRET: ${CLOUDINARY_API_SECRET}

  inventory-service:
    build:
      context: ./backend/inventory-service
      dockerfile: Dockerfile
    container_name: blinkit-inventory
    networks: [blinkit-network]
    ports:
      - "${INVENTORY_SERVICE_PORT}:8084"
    depends_on:
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SERVER_PORT: 8084
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/inventory_db
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}

  order-service:
    build:
      context: ./backend/order-service
      dockerfile: Dockerfile
    container_name: blinkit-order
    networks: [blinkit-network]
    ports:
      - "${ORDER_SERVICE_PORT}:8085"
    depends_on:
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SERVER_PORT: 8085
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/order_db
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}

  payment-service:
    build:
      context: ./backend/payment-service
      dockerfile: Dockerfile
    container_name: blinkit-payment
    networks: [blinkit-network]
    ports:
      - "${PAYMENT_SERVICE_PORT}:8086"
    depends_on:
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SERVER_PORT: 8086
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/payment_db
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}
      RAZORPAY_KEY_ID: ${RAZORPAY_KEY_ID}
      RAZORPAY_KEY_SECRET: ${RAZORPAY_KEY_SECRET}
      RAZORPAY_WEBHOOK_SECRET: ${RAZORPAY_WEBHOOK_SECRET}

  cart-service:
    build:
      context: ./backend/cart-service
      dockerfile: Dockerfile
    container_name: blinkit-cart
    networks: [blinkit-network]
    ports:
      - "${CART_SERVICE_PORT}:8087"
    depends_on:
      eureka-server:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SERVER_PORT: 8087
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      REDIS_HOST: redis
      REDIS_PORT: ${REDIS_PORT}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}

  delivery-service:
    build:
      context: ./backend/delivery-service
      dockerfile: Dockerfile
    container_name: blinkit-delivery
    networks: [blinkit-network]
    ports:
      - "${DELIVERY_SERVICE_PORT}:8088"
    depends_on:
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SERVER_PORT: 8088
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/delivery_db
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}

  notification-service:
    build:
      context: ./backend/notification-service
      dockerfile: Dockerfile
    container_name: blinkit-notification
    networks: [blinkit-network]
    ports:
      - "${NOTIFICATION_SERVICE_PORT}:8089"
    depends_on:
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SERVER_PORT: 8089
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/notification_db
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}
      SMTP_HOST: ${SMTP_HOST}
      SMTP_PORT: ${SMTP_PORT}
      SMTP_USERNAME: ${SMTP_USERNAME}
      SMTP_PASSWORD: ${SMTP_PASSWORD}
      TWILIO_ACCOUNT_SID: ${TWILIO_ACCOUNT_SID}
      TWILIO_AUTH_TOKEN: ${TWILIO_AUTH_TOKEN}
      TWILIO_PHONE_NUMBER: ${TWILIO_PHONE_NUMBER}
      FIREBASE_PROJECT_ID: ${FIREBASE_PROJECT_ID}

  coupon-service:
    build:
      context: ./backend/coupon-service
      dockerfile: Dockerfile
    container_name: blinkit-coupon
    networks: [blinkit-network]
    ports:
      - "${COUPON_SERVICE_PORT}:8090"
    depends_on:
      eureka-server:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SERVER_PORT: 8090
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/coupon_db
      REDIS_HOST: redis
      REDIS_PORT: ${REDIS_PORT}
      REDIS_PASSWORD: ${REDIS_PASSWORD}

  review-service:
    build:
      context: ./backend/review-service
      dockerfile: Dockerfile
    container_name: blinkit-review
    networks: [blinkit-network]
    ports:
      - "${REVIEW_SERVICE_PORT}:8091"
    depends_on:
      eureka-server:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SERVER_PORT: 8091
      EUREKA_SERVER_URL: ${EUREKA_SERVER_URL}
      MONGODB_URI: ${MONGO_URI}/review_db
      KAFKA_BOOTSTRAP_SERVERS: ${KAFKA_BOOTSTRAP_SERVERS}

  # ════════════════════════════════════════════════════
  # FRONTEND
  # ════════════════════════════════════════════════════

  customer-app:
    build:
      context: ./frontend/customer-app
      dockerfile: Dockerfile
    container_name: blinkit-customer-app
    networks: [blinkit-network]
    ports:
      - "3000:80"
    depends_on:
      - api-gateway

  admin-panel:
    build:
      context: ./frontend/admin-panel
      dockerfile: Dockerfile
    container_name: blinkit-admin-panel
    networks: [blinkit-network]
    ports:
      - "3001:80"
    depends_on:
      - api-gateway
```

---

### 12.6 Service Startup Order

Docker `depends_on` with `condition: service_healthy` enforces this exact boot sequence:

```
Layer 1 — Infrastructure (no dependencies)
  └── zookeeper
  └── redis

Layer 2 — Infrastructure dependent
  └── kafka  (needs: zookeeper healthy)

Layer 3 — Platform
  └── eureka-server  (needs: nothing, boots fast)
  └── config-server  (needs: eureka healthy)

Layer 4 — Application Services  (all need: eureka healthy)
  └── api-gateway        (also needs: redis)
  └── auth-service       (also needs: kafka, redis)
  └── product-service    (also needs: kafka, redis)
  └── cart-service       (also needs: kafka, redis)
  └── inventory-service  (also needs: kafka)
  └── order-service      (also needs: kafka)
  └── payment-service    (also needs: kafka)
  └── delivery-service   (also needs: kafka)
  └── notification-service (also needs: kafka)
  └── coupon-service     (also needs: redis)
  └── user-service
  └── review-service     (also needs: kafka)

Layer 5 — Frontend
  └── customer-app  (needs: api-gateway)
  └── admin-panel   (needs: api-gateway)
```

---

### 12.7 docker-compose.infra.yml — Infrastructure Only

Use this when you want to run only MongoDB/Redis/Kafka locally and run Spring Boot services from IntelliJ/IDE (faster dev loop):

```yaml
version: '3.9'

networks:
  blinkit-network:
    driver: bridge

volumes:
  redis-data:
  kafka-data:
  zookeeper-data:

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    container_name: blinkit-zookeeper
    networks: [blinkit-network]
    ports: ["2181:2181"]
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: blinkit-kafka
    networks: [blinkit-network]
    ports: ["9092:9092"]
    depends_on: [zookeeper]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    volumes:
      - kafka-data:/var/lib/kafka/data

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: blinkit-kafka-ui
    networks: [blinkit-network]
    ports: ["9093:8080"]
    depends_on: [kafka]
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092

  redis:
    image: redis:7-alpine
    container_name: blinkit-redis
    networks: [blinkit-network]
    ports: ["6379:6379"]
    command: redis-server --requirepass localpassword --appendonly yes
    volumes:
      - redis-data:/data

  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: blinkit-redis-ui
    networks: [blinkit-network]
    ports: ["8081:8081"]
    environment:
      REDIS_HOSTS: local:redis:6379:0:localpassword
```

**Run infra only:**
```bash
docker compose -f docker-compose.infra.yml up -d
```

This gives you:
- Kafka on `localhost:9092`
- Kafka UI on `localhost:9093`
- Redis on `localhost:6379`
- Redis Commander UI on `localhost:8081`

---

### 12.8 Useful Docker Commands

```bash
# ─── Start everything ─────────────────────────────────────────────
docker compose up -d

# ─── Start only infrastructure (dev mode) ─────────────────────────
docker compose -f docker-compose.infra.yml up -d

# ─── Build & start a single service after code change ─────────────
docker compose up -d --build auth-service

# ─── View live logs of a service ──────────────────────────────────
docker compose logs -f auth-service

# ─── View logs of multiple services ───────────────────────────────
docker compose logs -f api-gateway auth-service

# ─── Stop all services ────────────────────────────────────────────
docker compose down

# ─── Stop and delete all volumes (full reset) ─────────────────────
docker compose down -v

# ─── Check health of all containers ───────────────────────────────
docker compose ps

# ─── Open a shell inside a running container ──────────────────────
docker exec -it blinkit-auth sh

# ─── Check container resource usage ───────────────────────────────
docker stats

# ─── Build all images without starting ────────────────────────────
docker compose build

# ─── Remove unused images/containers to free disk space ───────────
docker system prune -a
```

---

### 12.9 docker-compose.prod.yml — Production Overrides

In production, override the base compose with these settings:

```yaml
version: '3.9'

services:

  api-gateway:
    restart: always
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: '0.5'
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  auth-service:
    restart: always
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: '0.5'
    # In prod, don't expose internal ports to host
    ports: []

  product-service:
    restart: always
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: '0.5'
    ports: []

  # Apply same pattern to all services:
  # restart: always
  # ports: []   (only api-gateway:8080 exposed externally)
  # resource limits set per service need

  customer-app:
    restart: always
    ports:
      - "80:80"

  admin-panel:
    restart: always
    ports:
      - "8090:80"
```

**Run in production:**
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

---

### 12.10 .dockerignore — Exclude Unnecessary Files from Build Context

Place in **each service folder** to speed up builds:

```
# Backend .dockerignore
target/
.git/
.gitignore
*.md
.env
.env.*
*.log
.idea/
.vscode/
```

```
# Frontend .dockerignore
node_modules/
dist/
.git/
.gitignore
*.md
.env
.env.*
*.log
```

---

### 12.11 Inter-Container Communication

All services share `blinkit-network`. Inside Docker, services call each other by **container name** (Docker's internal DNS):

```
From any service inside Docker:
  Kafka:         kafka:29092         (NOT localhost:9092)
  Redis:         redis:6379
  Eureka:        eureka-server:8761
  Auth service:  auth-service:8081
  ...etc

From your host machine (browser/Postman/IDE):
  API Gateway:   localhost:8080
  Kafka:         localhost:9092
  Redis:         localhost:6379
  Eureka UI:     localhost:8761
```

This is why each service's `application.yml` should read hosts from environment variables:

```yaml
# application.yml pattern for all services
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka}
```

---

## TECH VERSIONS

| Technology | Purpose | Version |
|---|---|---|
| Java | Backend language | 21 |
| Spring Boot | Microservice framework | 3.3.x |
| Spring Cloud Gateway | API Gateway | 2023.x |
| Spring Cloud Netflix Eureka | Service discovery | 2023.x |
| Spring Cloud Config | Centralised config | 2023.x |
| Spring Cloud OpenFeign | Inter-service HTTP calls | 2023.x |
| Resilience4j | Circuit breaker | 2.x |
| MongoDB Atlas | Primary database (per service) | 7.x |
| Redis | Cart, tokens, cache | 7.x |
| Apache Kafka | Async event bus | 3.x |
| JJWT | JWT token generation | 0.12.5 |
| Cloudinary | Product image storage | latest |
| Razorpay SDK | Payment processing | latest |
| Twilio | SMS notifications | 8.x |
| Firebase FCM | Push notifications | latest |
| springdoc-openapi | Swagger UI per service | 2.x |
| React | Frontend UI | 18 |
| Vite | React build tool | 5.x |
| React Router | Client-side routing | v6 |
| Zustand | Frontend state (cart, auth) | 4.x |
| Axios | HTTP client + interceptors | 1.x |
| Tailwind CSS | Styling | 3.x |
| Docker Engine | Container runtime | 26.x |
| Docker Compose | Multi-container orchestration | v2.x |
| eclipse-temurin:21-jre-alpine | Spring Boot runtime image | 21-jre |
| nginx:alpine | Frontend static file server | latest |
| node:20-alpine | React build image | 20 |

---

## 13. API RESPONSE CODES — STANDARD CONTRACT

Every API returns this wrapper regardless of success or failure:
```json
{
  "success": true | false,
  "message": "Human readable message",
  "data": { ... } | null
}
```

### Standard HTTP Codes Used

| Code | Name | When | `success` |
|------|------|------|-----------|
| `200` | OK | Successful GET, PUT, DELETE, non-creating POST | `true` |
| `201` | Created | New resource created | `true` |
| `400` | Bad Request | Validation failed, missing/malformed input, business rule violation | `false` |
| `401` | Unauthorized | No token, expired token, blacklisted token, invalid credentials | `false` |
| `403` | Forbidden | Authenticated but not allowed (unverified, deactivated, wrong role) | `false` |
| `404` | Not Found | Resource doesn't exist with given ID | `false` |
| `409` | Conflict | Duplicate / already exists | `false` |
| `410` | Gone | Resource existed but is now expired/consumed | `false` |
| `500` | Internal Server Error | Unexpected exception — always logged server-side | `false` |

---

### Per-Service Response Code Plan

#### Auth Service (`/api/auth/**`)

| Endpoint | `201` | `200` | `400` | `401` | `403` | `404` | `409` | `410` |
|----------|-------|-------|-------|-------|-------|-------|-------|-------|
| `POST /signup` | ✅ success | — | validation fail | — | — | — | email taken | — |
| `GET /verify` | — | ✅ success | bad/expired OTP, already verified | — | — | user not found | — | — |
| `POST /login` | — | ✅ success | validation fail | wrong password | unverified / deactivated | user not found | — | — |
| `POST /refresh` | — | ✅ success | missing fields | invalid/expired refresh token | — | user not found | — | — |
| `POST /logout` | — | ✅ success | — | invalid token (gateway) | — | — | — | — |
| `POST /forgot-password` | — | ✅ success | validation fail | — | — | user not found | — | — |
| `GET /reset-password/validate/{token}` | — | ✅ valid | — | — | — | — | — | expired/invalid |
| `POST /reset-password/{token}` | — | ✅ success | validation fail | — | — | — | — | expired/invalid |

#### User Service (`/api/users/**`)

| Endpoint | `201` | `200` | `400` | `401` | `404` |
|----------|-------|-------|-------|-------|-------|
| `GET /profile` | — | ✅ success | — | no token | profile not found |
| `PUT /profile` | — | ✅ success | validation fail | no token | — |
| `GET /addresses` | — | ✅ success | — | no token | — |
| `POST /addresses` | ✅ success | — | validation fail | no token | — |
| `PUT /addresses/{id}` | — | ✅ success | validation fail | no token | not found |
| `DELETE /addresses/{id}` | — | ✅ success | — | no token | not found |
| `PUT /addresses/{id}/default` | — | ✅ success | — | no token | not found |

#### Product Service (`/api/products/**, /api/categories/**`)

| Endpoint | `201` | `200` | `400` | `401` | `403` | `404` | `409` |
|----------|-------|-------|-------|-------|-------|-------|-------|
| `GET /products` | — | ✅ success | — | — | — | — | — |
| `GET /products/{id}` | — | ✅ success | — | — | — | not found | — |
| `GET /products/search?q=` | — | ✅ success | missing `q` param | — | — | — | — |
| `GET /products/category/{slug}` | — | ✅ success | — | — | — | — | — |
| `POST /products/admin` | ✅ success | — | validation / sellingPrice > mrp | no token | not ADMIN | bad categoryId | duplicate slug |
| `PUT /products/admin/{id}` | — | ✅ success | validation fail | no token | not ADMIN | not found | — |
| `PUT /products/admin/{id}/toggle` | — | ✅ success | — | no token | not ADMIN | not found | — |
| `DELETE /products/admin/{id}` | — | ✅ success | — | no token | not ADMIN | not found | — |
| `GET /categories` | — | ✅ success | — | — | — | — | — |
| `POST /categories/admin` | ✅ success | — | missing fields | no token | not ADMIN | bad parentId | duplicate name |

#### Inventory Service (`/api/inventory/**`)

| Endpoint | `200` | `400` | `401` | `403` | `404` | `409` |
|----------|-------|-------|-------|-------|-------|-------|
| `GET /inventory/{productId}` | ✅ success | — | no token | — | not found | — |
| `GET /inventory/admin` | ✅ success | — | no token | not ADMIN | — | — |
| `PUT /inventory/admin/{productId}` | ✅ success | — | no token | not ADMIN | not found | — |
| `POST /inventory/reserve` | ✅ success | — | no token | — | product not found | insufficient stock |
| `POST /inventory/release` | ✅ success | — | no token | — | not found | — |
| `POST /inventory/confirm` | ✅ success | — | no token | — | not found | — |

---

### Frontend Handling Rules

```
200 / 201 → success → show data or success toast
400       → show field-level validation errors from message
401       → token expired or missing → redirect to login
403       → show "Access Denied" or "Please verify your email" based on message
404       → show empty state or "Not Found" page
409       → show inline conflict error (e.g. "Email already taken")
410       → show "Link expired, please request a new one"
500       → show generic "Something went wrong. Try again." toast
```

---

*Plan created: 2026-03-15 | Based on journalApp JWT implementation*
