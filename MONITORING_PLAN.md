# Monitoring Implementation — Blinkit Clone

> **Status: Fully Implemented ✅**
> Last updated: 2026-03-26

---

## Architecture Overview

```
Spring Boot Services (15)
  + Node Exporter (host OS)
        │
        │  scrape every 15s
        ▼
   Prometheus (Docker, port 9090)
        │
        │  query
        ▼
    Grafana (Docker, port 3000)
        │
        │  alert → SMTP (Gmail)
        ▼
  Email → rahul2140kumar@gmail.com
```

---

## Prometheus Scrape Targets

**Total: 16 jobs** (15 services + node-exporter)

| Job | Port | Metrics Path | Notes |
|-----|------|-------------|-------|
| `node-exporter` | 9100 | `/metrics` | Host OS: CPU, RAM, Disk, Network |
| `eureka-server` | 8761 | `/actuator/prometheus` | Service discovery |
| `config-server` | 8888 | `/actuator/prometheus` | Centralised config |
| `api-gateway` | 8080 | `/actuator/prometheus` | |
| `auth-service` | 8081 | `/actuator/prometheus` | |
| `user-service` | 8082 | `/actuator/prometheus` | |
| `product-service` | 8083 | `/actuator/prometheus` | |
| `inventory-service` | 8084 | `/actuator/prometheus` | |
| `order-service` | 8085 | `/actuator/prometheus` | |
| `payment-service` | 8086 | `/actuator/prometheus` | |
| `cart-service` | 8087 | `/actuator/prometheus` | |
| `delivery-service` | 8088 | `/actuator/prometheus` | |
| `notification-service` | 8089 | `/actuator/prometheus` | |
| `coupon-service` | 8090 | `/actuator/prometheus` | |
| `review-service` | 8091 | `/actuator/prometheus` | |
| `metrics-exporter` | 8092 | `/metrics` | Custom business + infra metrics |

**Scrape interval:** 15s | **Retention:** 15 days | **Estimated disk:** ~1 GB

---

## metrics-exporter Service

| Property | Value |
|----------|-------|
| Module | `backend/metrics-exporter/` |
| Port | `8092` |
| Spring app name | `metrics-exporter` |
| Package | `com.blinkit.metricsexporter` |
| Config file | `config-server/configs/metrics-exporter.yml` |

### How it works

```
Prometheus GET /metrics (every 15s)
    │
    ▼
MetricsController → MetricsService.collectAllMetrics()
    │
    ├── registry.clear()
    ├── collectDeliveryPartnerMetrics()   ← queries delivery_db directly
    ├── collectOrderStatusCounts()        ← queries order_db directly
    ├── collectOrderInfoMetrics()         ← reads OrderInfoCache (refreshed every 30s)
    ├── collectInventoryMetrics()         ← queries inventory_db directly
    ├── collectInventoryInfoMetrics()     ← reads InventoryInfoCache (refreshed every 30s)
    └── collectInfraHealthMetrics()       ← reads InfraHealthCache (refreshed every 30min)
    │
    └── registry.scrape() → Prometheus text format
```

**Dedicated registry** (`businessMetricsRegistry`) — isolated from Spring Boot's global registry so `clear()` is safe.

---

## Custom Business Metrics

### Delivery Partner Metrics

| Metric | Description |
|--------|-------------|
| `blinkit_delivery_partners_available` | Partners ready for assignment |
| `blinkit_delivery_partners_unavailable` | Partners currently on a delivery |
| `blinkit_delivery_partners_total` | All active registered partners |

Source: `delivery_db.delivery_partners`

---

### Order Status Counts

| Metric | Description |
|--------|-------------|
| `blinkit_orders_pending` | Orders waiting to be confirmed |
| `blinkit_orders_confirmed` | Orders confirmed and in progress |
| `blinkit_orders_delivered` | Orders successfully delivered |
| `blinkit_orders_cancelled` | Orders cancelled |

Source: `order_db.orders`

---

### Order Info Metric (Info Pattern)

**Metric:** `blinkit_order_info` — value always `1.0`, all fields stored as tags.

| Tag | Example | Description |
|-----|---------|-------------|
| `order_number` | `BLK-20260326-0001` | Human-readable order ID |
| `order_status` | `DELIVERED` | PENDING / CONFIRMED / DELIVERED / CANCELLED |
| `payment_status` | `SUCCESS` | SUCCESS / PENDING / FAILED / NO_PAYMENT |
| `amount` | `349.00` | Total order amount (₹) |
| `delivery_min` | `28` | Minutes from order creation to delivery |
| `created_at` | `2026-03-26 14:22` | IST formatted timestamp |

**Source:** `OrderInfoCache` — refreshed every 30s, queries:
1. `order_db.orders` — orders from last 12 hours
2. `payment_db.transactions` — payment status per order
3. `delivery_db.delivery_tasks` — delivery status + actual delivery time

---

### Inventory Aggregate Metrics

| Metric | Description |
|--------|-------------|
| `blinkit_inventory_low_stock` | Products with `availableQty` between 1 and threshold |
| `blinkit_inventory_out_of_stock` | Products with `availableQty = 0` |

Source: `inventory_db.stock`

---

### Inventory Info Metric (Info Pattern)

**Metric:** `blinkit_inventory` — value always `1.0`, all fields stored as tags.

| Tag | Example | Description |
|-----|---------|-------------|
| `product_id` | `uuid` | Product ID |
| `product_name` | `Tomato 500g` | Product name |
| `available_qty` | `6` | Current available quantity |
| `reserved_qty` | `0` | Currently reserved quantity |
| `stock_status` | `LOW_STOCK` | IN_STOCK / LOW_STOCK / OUT_OF_STOCK |
| `low_stock_threshold` | `10` | Threshold below which LOW_STOCK triggers |
| `is_available` | `true` | Whether product is enabled by admin |
| `last_restocked` | `2026-03-26 13:56` | IST formatted last restock time |

**Source:** `InventoryInfoCache` — refreshed every 30s, 2 MongoDB queries:
1. Batch fetch unavailable `productId`s from `product_db.products`
2. Filter at MongoDB level using `$expr` + `$ifNull` + `$or` — no Java-side filtering

Shows only products needing attention: out of stock, low stock, or admin-disabled.

---

## Infrastructure Health Metrics

### How Health Checks Work

`InfraHealthCache` runs every **30 minutes** and tests each component:

| Component | Test | Pass Condition |
|-----------|------|----------------|
| **MONGODB** | Insert doc → read back → verify → delete in `infra_health_check_db` | All 3 sub-steps succeed |
| **REDIS** | `SETEX` key → `GET` → verify value → `DEL` | Value matches, no errors |
| **KAFKA** | `AdminClient.listTopics()` with 5s timeout | Topics list returned |
| **CDN** | HTTP GET to `https://api.cloudinary.com/v1_1/{cloud}/ping` (authenticated) | HTTP 200 |
| **OVERALL** | All 4 above pass | Every component = RUNNING |

### Prometheus Metrics

| Metric | Labels | Description |
|--------|--------|-------------|
| `blinkit_infra_status` | `component` | `1` = Healthy, `0` = Failing |
| `blinkit_infra_response_ms` | `component` | Response time of last health check (ms) |
| `blinkit_infra` | `component, status, response_ms, last_checked, error` | Info metric → Grafana table |

### Resilience — metrics-exporter stays up even if infra goes down

| Scenario | Result |
|----------|--------|
| MongoDB down at startup | ✅ Starts fine — 3s `serverSelectionTimeout`, caught by try/catch |
| MongoDB down at runtime | ✅ Caches serve stale data, infra shows FAILED |
| Redis down | ✅ No Spring Data Redis dependency — raw Jedis in try/catch |
| Kafka down | ✅ `fail-fast=false`, AdminClient in try/catch |
| CDN down | ✅ Pure HTTP call in try/catch |

---

## Grafana Dashboards

### Dashboards

| Dashboard | File | Description |
|-----------|------|-------------|
| Blinkit — Custom Business Metrics (Stage 3) | `blinkit-stage3-custom.json` | **Main unified dashboard** |
| Blinkit — System Resources & Configuration | `blinkit-system.json` | Host + JVM resource panels |
| Blinkit — Stage 2: All Services Overview | `blinkit-stage2.json` | Per-service traffic & resource tables |
| Blinkit — Stage 1: user-service Metrics | `blinkit-stage1.json` | Original single-service dashboard |
| Blinkit — Mac System Resources | `blinkit-mac-system.json` | Mac-specific host metrics |

### Stage 3 Dashboard — Panel Layout

```
▶ System Resources & Configuration    (collapsed — 25 panels)
   CPU Cores | Total RAM | Disk | OS | Services Running | Services Down
   CPU Usage % | RAM Used/Available | Disk Used/Free | JVM totals | Threads
   Time series: CPU / RAM / Disk over time

▶ All Services Overview               (collapsed — 7 panels)
   Service Health table (status / CPU / Heap / uptime / req rate / errors)
   CPU / Heap / HTTP rate / response time / threads / RAM time series

▶ Infrastructure Health               (row — always visible)
   Table: Infra Name | Is Healthy | Response (ms) | Last Checked | Error

── MongoDB / Business Metrics ─────────────────────────────────
   Delivery Partners: Available | On Delivery | Total | Rate %
   Delivery Partner availability over time (time series)

   Orders: Pending | Confirmed | Delivered | Cancelled
   Order Status pie chart | Order counts over time

   Low Stock count (stat) | Out of Stock count (stat)

   Order Details table — last 12 hours
     Created At | Order Number | Order Status | Payment | Amount | Delivery min

   Product Inventory table — unavailable / low stock / out of stock
     Last Restocked | Product Name | Avail Qty | Min Stock | Reserved Qty | Stock Status | Available
```

---

## Grafana Alerting

### Email Alerts (Grafana Unified Alerting)

All SMTP credentials come from `.env.dev` — nothing hardcoded in config files.

| Setting | Value |
|---------|-------|
| SMTP Host | `smtp.gmail.com:587` |
| From | `velinq.dev@gmail.com` (Gmail App Password) |
| To | `rahul2140kumar@gmail.com` (`${ADMIN_EMAIL}`) |
| StartTLS | MandatoryStartTLS |

### Alert Rules

| Rule | UID | Fires after | Condition |
|------|-----|-------------|-----------|
| Infrastructure Component Down | `infra-comp-down-v2` | 1 min | Any single component `blinkit_infra_status < 1` |
| Blinkit Infrastructure Degraded | `infra-overall-down-v2` | 2 min | OVERALL `blinkit_infra_status < 1` |

**Notification policy:** group by `alertname + component`, repeat every 2h while firing.
**Recovery emails:** sent automatically when alert resolves.

---

## How to Start the Monitoring Stack

```bash
cd backend

# 1. Start Colima (Docker runtime) if not running
colima start

# 2. Source env vars (required for docker compose variable substitution)
set -o allexport && source .env.dev && set +o allexport

# 3. Start Prometheus + Grafana + Node Exporter
docker compose -f docker-compose.monitoring.yml up -d

# 4. Access
#    Grafana:    http://localhost:3000  (admin / admin)
#    Prometheus: http://localhost:9090
```

### Useful URLs

| URL | Description |
|-----|-------------|
| http://localhost:3000 | Grafana (login: from `.env.dev` `GRAFANA_ADMIN_USER/PASSWORD`) |
| http://localhost:9090/targets | Prometheus scrape targets — all should be UP |
| http://localhost:9090/graph | Prometheus query explorer |
| http://localhost:8092/metrics | Raw custom metrics output |

---

## Key Files

| File | Purpose |
|------|---------|
| `backend/prometheus.yml` | Prometheus scrape config (16 jobs) |
| `backend/docker-compose.monitoring.yml` | Prometheus + Grafana + Node Exporter containers |
| `backend/grafana/provisioning/datasources/` | Grafana datasource (Prometheus) |
| `backend/grafana/provisioning/dashboards/` | Grafana dashboard provisioning config |
| `backend/grafana/provisioning/alerting/infra-alerts.yml` | Alert rules + contact point |
| `backend/grafana/dashboards/` | All dashboard JSON files |
| `backend/metrics-exporter/` | Custom metrics service |
| `backend/config-server/configs/metrics-exporter.yml` | metrics-exporter config (Mongo URI, Cloudinary) |

---

## Prometheus Storage Estimate

```
16 scrape targets × ~300 metrics each  = ~4,800 time series
Scrape interval                         = 15s → ~320 samples/sec
Bytes per sample (TSDB compressed)      = ~2 bytes
Retention                               = 15 days = 1,296,000 seconds

Disk = 1,296,000 × 320 × 2 ≈ 830 MB (~1 GB to be safe)
```

**1 GB for 15 days is negligible on the 47 GB Oracle VM disk.**
