# Mock Bank Payment Plan — Blinkit Clone

> **Why this exists:**
> We don't have a real payment gateway (Razorpay) available. Instead, we simulate a bank:
> - Every new user gets a **default wallet balance of ₹10,000** on registration.
> - When they place an order, money is **debited from their wallet**.
> - On cancellation, money is **refunded back to the wallet**.
> - Admins can top-up any wallet.
>
> This replaces the Razorpay integration entirely. The rest of the system (order-service, Kafka events, inventory, notifications) works exactly as originally planned — only the payment mechanism changes.

---

## Services Involved

| Service | Port | Role |
|---------|------|------|
| `payment-service` | 8086 | Wallet management + payment processing |
| `order-service` | 8085 | Creates orders, calls payment-service (Feign) |
| `notification-service` | 8089 | Sends payment confirmation / failure emails |

---

## MongoDB Collections (`payment_db`)

### 1. `wallets`
Stores each user's wallet.

```json
{
  "_id": "mongo-generated",
  "walletId": "uuid-v4",
  "userId": "uuid (same as AuthUser.userId)",
  "balance": 10000.00,
  "currency": "INR",
  "isActive": true,
  "createdAt": "Instant",
  "updatedAt": "Instant"
}
```

**Indexes:**
- `userId` — unique

---

### 2. `transactions`
Full audit trail of every debit/credit.

```json
{
  "_id": "mongo-generated",
  "transactionId": "uuid-v4",
  "walletId": "uuid",
  "userId": "uuid",
  "orderId": "uuid (null for top-up)",
  "type": "DEBIT | CREDIT",
  "reason": "ORDER_PAYMENT | ORDER_REFUND | ADMIN_TOPUP | SIGNUP_BONUS",
  "amount": 349.00,
  "balanceBefore": 10000.00,
  "balanceAfter": 9651.00,
  "status": "SUCCESS | FAILED",
  "description": "human-readable note",
  "createdAt": "Instant"
}
```

**Indexes:**
- `userId + createdAt` (list history)
- `orderId` (lookup by order)
- `transactionId` unique

---

## Kafka Events

### Events consumed by payment-service
| Topic | Action |
|-------|--------|
| `user.registered` | Create wallet with ₹10,000 signup bonus |
| `order.cancelled` | Refund order amount back to wallet |

### Events published by payment-service
| Topic | When |
|-------|------|
| `payment.success` | After successful wallet debit |
| `payment.failed` | If wallet balance insufficient |

---

## Kafka Event POJOs

### `PaymentSuccessEvent` (published by payment-service)
```java
String paymentId      // transactionId
String orderId
String userId
double amount
Instant paidAt
```

### `PaymentFailedEvent` (published by payment-service)
```java
String orderId
String userId
double amount
String reason         // e.g. "Insufficient wallet balance"
Instant failedAt
```

---

## REST Endpoints

### Customer Endpoints (JWT required)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/payments/wallet` | View wallet balance + walletId |
| GET | `/api/payments/history` | Paginated transaction history |
| GET | `/api/payments/history/{transactionId}` | Single transaction detail |

### Internal Endpoints (X-Internal-Secret required, blocked at gateway)

| Method | Path | Called by | Description |
|--------|------|-----------|-------------|
| POST | `/api/payments/pay` | order-service (Feign) | Debit wallet for an order |
| POST | `/api/payments/refund/{orderId}` | order-service (Feign) | Refund order amount to wallet |

### Admin Endpoints (ADMIN role required)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/payments/admin/wallets` | List all wallets (paginated) |
| GET | `/api/payments/admin/wallets/{userId}` | View a specific user's wallet |
| POST | `/api/payments/admin/wallets/{userId}/topup` | Add money to a user's wallet |
| GET | `/api/payments/admin/transactions` | All transactions (filterable by userId, type, date) |

---

## Request / Response DTOs

### `POST /payments/pay` (internal)
**Request:**
```json
{
  "orderId": "uuid",
  "userId": "uuid",
  "amount": 349.00,
  "description": "Payment for order BLK-20260321-0001"
}
```
**Response (200):**
```json
{
  "success": true,
  "message": "Payment successful",
  "data": {
    "transactionId": "uuid",
    "walletBalance": 9651.00,
    "amount": 349.00,
    "status": "SUCCESS"
  }
}
```
**Response (400) — insufficient balance:**
```json
{
  "success": false,
  "message": "Insufficient wallet balance. Available: ₹200.00, Required: ₹349.00",
  "data": null
}
```

---

### `POST /payments/refund/{orderId}` (internal)
**Response (200):**
```json
{
  "success": true,
  "message": "Refund of ₹349.00 credited to wallet",
  "data": {
    "transactionId": "uuid",
    "walletBalance": 10000.00,
    "amount": 349.00,
    "status": "SUCCESS"
  }
}
```

---

### `GET /payments/wallet`
```json
{
  "success": true,
  "message": "Wallet details",
  "data": {
    "walletId": "uuid",
    "balance": 9651.00,
    "currency": "INR"
  }
}
```

---

### `POST /payments/admin/wallets/{userId}/topup`
**Request:**
```json
{
  "amount": 5000.00,
  "description": "Manual top-up by admin"
}
```

---

## Full Purchase Flow (with mock bank)

```
Customer → POST /api/orders
  │
  ├─ order-service fetches cart (Feign → cart-service)
  ├─ order-service validates coupon if applied (Feign → coupon-service)
  ├─ order-service reserves stock (Feign → inventory-service)
  ├─ order-service saves Order {status=PAYMENT_PENDING}
  ├─ order-service calls payment-service (Feign → POST /payments/pay)
  │    ├─ [SUCCESS] wallet debited
  │    │     ├─ payment-service publishes payment.success
  │    │     └─ order-service receives 200 → sets status=PAYMENT_PROCESSING
  │    └─ [FAIL] insufficient balance
  │          ├─ payment-service publishes payment.failed
  │          ├─ order-service receives 400 → sets status=PAYMENT_FAILED
  │          └─ inventory-service stock released (Feign → /inventory/release)
  │
  └─ Kafka: payment.success consumed by order-service
       ├─ order-service sets status=CONFIRMED
       ├─ publishes order.confirmed
       │    ├─ inventory-service: confirms stock deduction
       │    ├─ cart-service: clears cart
       │    ├─ notification-service: sends confirmation email
       │    └─ delivery-service: creates delivery task (stub)
       └─ order-service calls coupon-service to record coupon usage (Feign)
```

---

## Cancellation / Refund Flow

```
Customer → POST /api/orders/{id}/cancel
  │
  ├─ order-service validates: status must be PAYMENT_PENDING/CONFIRMED/PACKED
  ├─ order-service sets status=CANCELLED
  ├─ publishes order.cancelled
  │    ├─ inventory-service: releases reserved stock
  │    └─ notification-service: sends cancellation email
  │
  └─ payment-service consumes order.cancelled
       ├─ finds transaction by orderId
       ├─ if status=SUCCESS → refunds wallet (CREDIT transaction)
       └─ publishes payment.refunded (notification-service sends refund email)
```

---

## User Registration → Wallet Creation Flow

```
auth-service publishes user.registered
  └─ payment-service consumes user.registered
       ├─ creates Wallet {userId, balance=10000.00, isActive=true}
       └─ creates Transaction {type=CREDIT, reason=SIGNUP_BONUS, amount=10000.00}
```

---

## Concurrency Safety

Wallet debit must be **atomic** — use MongoDB `findOneAndUpdate` with conditional update:

```
db.wallets.findOneAndUpdate(
  { userId: X, balance: { $gte: amount } },
  { $inc: { balance: -amount } },
  { returnDocument: "after" }
)
```

If no document is returned → balance was insufficient → return 400.

This avoids double-spend without needing distributed locks.

---

## Order Status State Machine

```
PAYMENT_PENDING
    │
    ├─ [payment success] → PAYMENT_PROCESSING → CONFIRMED
    │                                              │
    │                                         PACKED → OUT_FOR_DELIVERY → DELIVERED
    │
    └─ [payment failed]  → PAYMENT_FAILED

CONFIRMED / PACKED → [user/admin cancel] → CANCELLED (triggers refund)
```

---

## Feign Clients (order-service)

```java
// PaymentServiceClient.java
POST /payments/pay           → PaymentResponse
POST /payments/refund/{orderId} → PaymentResponse

// InventoryServiceClient.java
POST /inventory/reserve      → ReserveResponse
POST /inventory/release      → void
POST /inventory/confirm      → void

// CouponServiceClient.java
POST /coupons/validate       → ValidateCouponResponse
POST /coupons/record-usage   → void

// CartServiceClient.java
GET  /cart                   → CartResponse (internal)
```

All Feign clients inject `X-Internal-Secret` header via `FeignConfig`.

---

## Kafka Topics Active After Stage 5

| Topic | Publisher | Consumers |
|-------|-----------|-----------|
| `user.registered` | auth-service | notification-service, user-service, **payment-service** |
| `user.password.reset` | auth-service | notification-service |
| `order.created` | order-service | notification-service |
| `order.confirmed` | order-service | inventory-service, notification-service, cart-service, delivery-service |
| `order.cancelled` | order-service | inventory-service, notification-service, **payment-service** |
| `order.status.updated` | order-service | notification-service |
| `payment.success` | payment-service | order-service, notification-service |
| `payment.failed` | payment-service | order-service, notification-service |

---

## Notification Emails Triggered

| Event | Email |
|-------|-------|
| Wallet created (signup) | "Welcome! ₹10,000 added to your Blinkit wallet." |
| Payment success | "Payment of ₹X confirmed for order BLK-..." |
| Payment failed | "Payment failed — insufficient balance. Please top up your wallet." |
| Order confirmed | "Your order BLK-... is confirmed and being packed!" |
| Order cancelled | "Your order has been cancelled." |
| Refund processed | "₹X refunded to your wallet for order BLK-..." |

---

## Config Server (`payment-service.yml`)

```yaml
spring:
  data:
    mongodb:
      database: payment_db
```

## API Gateway Route

```yaml
- id: payment-service
  uri: lb://payment-service
  predicates:
    - Path=/api/payments/**
  filters:
    - StripPrefix=1
```

**Public paths:** none (all payment routes require JWT or internal secret)

**Gateway blocks `/api/payments/pay` and `/api/payments/refund/**`** — internal Feign only.

---

## Package Structure

```
payment-service/
├── PaymentServiceApplication.java
├── config/
│   ├── MongoConfig.java
│   ├── FeignConfig.java              ← injects X-Internal-Secret on outbound calls
│   ├── KafkaConsumerConfig.java
│   └── InternalRequestFilter.java   ← blocks /pay and /refund/* from external callers
├── controller/
│   ├── WalletController.java         ← GET /wallet, GET /history
│   ├── PaymentController.java        ← POST /pay, POST /refund/{orderId} (internal)
│   └── AdminPaymentController.java   ← admin wallet/transaction views + top-up
├── dto/
│   ├── request/
│   │   ├── PayRequest.java
│   │   └── TopUpRequest.java
│   └── response/
│       ├── WalletResponse.java
│       ├── PaymentResponse.java
│       └── TransactionResponse.java
├── entity/
│   ├── Wallet.java
│   └── Transaction.java
├── event/
│   ├── UserRegisteredEvent.java      ← consumed
│   ├── OrderCancelledEvent.java      ← consumed
│   ├── PaymentSuccessEvent.java      ← published
│   └── PaymentFailedEvent.java       ← published
├── exception/
│   └── GlobalExceptionHandler.java
├── kafka/
│   └── PaymentEventPublisher.java    ← publishes payment.success / payment.failed
├── consumer/
│   └── PaymentEventConsumer.java     ← consumes user.registered + order.cancelled
├── repository/
│   ├── WalletRepository.java
│   └── TransactionRepository.java
└── service/
    └── PaymentService.java
```

---

## Test Plan

| # | Test | Expected |
|---|------|----------|
| T1 | Register new user → GET /wallet | Balance = ₹10,000 |
| T2 | POST /orders with valid cart | Order created, wallet debited, status = CONFIRMED |
| T3 | GET /payments/wallet after purchase | Balance = 10000 - orderTotal |
| T4 | GET /payments/history | Shows DEBIT transaction with correct amount |
| T5 | POST /orders/{id}/cancel | Status = CANCELLED, wallet refunded |
| T6 | GET /payments/history after refund | Shows CREDIT transaction |
| T7 | POST /orders when balance < total | 400 "Insufficient wallet balance" |
| T8 | Admin top-up wallet | Balance increases, CREDIT transaction logged |
| T9 | Two concurrent orders (race condition) | Only one succeeds if combined total > balance |
| T10 | Apply coupon → place order | Discounted total debited, not original total |

---

## Done When

Full purchase flow works end-to-end:
1. New user has ₹10,000 wallet after signup
2. Add items to cart → apply coupon → place order
3. Wallet balance decreases by `order.totalAmount`
4. Order status moves: `PAYMENT_PENDING → CONFIRMED`
5. Stock decremented in inventory
6. Cart cleared
7. Confirmation email received
8. Cancel order → refund email → wallet balance restored
