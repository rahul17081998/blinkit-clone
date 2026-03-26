package com.blinkit.metricsexporter.service;

import com.blinkit.metricsexporter.cache.InfraHealthCache;
import com.blinkit.metricsexporter.cache.InventoryInfoCache;
import com.blinkit.metricsexporter.cache.OrderInfoCache;
import com.blinkit.metricsexporter.dto.InfraHealthPayload;
import com.blinkit.metricsexporter.dto.InventoryInfoPayload;
import com.blinkit.metricsexporter.dto.OrderInfoPayload;
import com.mongodb.client.MongoClient;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MongoClient mongoClient;
    private final OrderInfoCache orderInfoCache;
    private final InventoryInfoCache inventoryInfoCache;
    private final InfraHealthCache infraHealthCache;

    @Qualifier("businessMetricsRegistry")
    private final PrometheusMeterRegistry registry;

    /**
     * Called on every Prometheus scrape (every 15s via GET /metrics).
     *
     * Flow:
     *   1. Clear previous gauges from the dedicated registry
     *   2. Each collect method runs its logic and registers fresh gauges
     *   3. Serialize to Prometheus text format and return
     */
    public String collectAllMetrics() {
        registry.clear();

        collectDeliveryPartnerMetrics();
        collectOrderStatusCounts();
        collectOrderInfoMetrics();
        collectInventoryMetrics();
        collectInventoryInfoMetrics();
        collectInfraHealthMetrics();

        return registry.scrape();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delivery Partner Metrics  (delivery_db)
    // ─────────────────────────────────────────────────────────────────────────

    private void collectDeliveryPartnerMetrics() {
        double available   = count("delivery_db", "delivery_partners",
                Criteria.where("isAvailable").is(true).and("isActive").is(true));
        double unavailable = count("delivery_db", "delivery_partners",
                Criteria.where("isAvailable").is(false).and("isActive").is(true));
        double total       = count("delivery_db", "delivery_partners",
                Criteria.where("isActive").is(true));

        Gauge.builder("blinkit_delivery_partners_available",   () -> available)
                .description("Partners ready for assignment").register(registry);
        Gauge.builder("blinkit_delivery_partners_unavailable", () -> unavailable)
                .description("Partners currently on a delivery").register(registry);
        Gauge.builder("blinkit_delivery_partners_total",       () -> total)
                .description("All active registered delivery partners").register(registry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Order Status Counts  (order_db) — aggregate totals per status
    // ─────────────────────────────────────────────────────────────────────────

    private void collectOrderStatusCounts() {
        double pending   = count("order_db", "orders", Criteria.where("status").is("PENDING"));
        double confirmed = count("order_db", "orders", Criteria.where("status").is("CONFIRMED"));
        double delivered = count("order_db", "orders", Criteria.where("status").is("DELIVERED"));
        double cancelled = count("order_db", "orders", Criteria.where("status").is("CANCELLED"));

        Gauge.builder("blinkit_orders_pending",   () -> pending)
                .description("Orders waiting to be confirmed").register(registry);
        Gauge.builder("blinkit_orders_confirmed", () -> confirmed)
                .description("Orders confirmed and in progress").register(registry);
        Gauge.builder("blinkit_orders_delivered", () -> delivered)
                .description("Orders successfully delivered").register(registry);
        Gauge.builder("blinkit_orders_cancelled", () -> cancelled)
                .description("Orders cancelled").register(registry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Order Info Metrics  (per-order detail, read from OrderInfoCache)
    //
    // Pattern: Prometheus "info metric"
    //   - One gauge per order (last 50 orders, refreshed every 30s by the cache)
    //   - gauge value = 1  (just a marker — the real data is in the tags)
    //   - tags carry: order_number, order_status, payment_status,
    //                 delivery_status, amount, delivery_minutes
    //
    // In Grafana → Table visualization → "Labels to fields" transform
    // → each tag becomes a column, each order becomes a row
    // ─────────────────────────────────────────────────────────────────────────

    private void collectOrderInfoMetrics() {
        Map<String, OrderInfoPayload> orders = orderInfoCache.snapshot();

        for (OrderInfoPayload p : orders.values()) {
            // capture locals for lambda — required since variables used in lambda must be effectively final
            final double deliveryMinutes = p.getDeliveryMinutes();
            final double totalAmount     = p.getTotalAmount();

            // Info metric: value = 1, all fields in tags
            Gauge.builder("blinkit_order_info", () -> 1.0)
                    .description("Per-order status snapshot")
                    .tag("order_number",   p.getOrderNumber())
                    .tag("order_status",   p.getOrderStatus())
                    .tag("payment_status", p.getPaymentStatus())
                    .tag("amount",         String.format("%.2f", totalAmount))
                    .tag("delivery_min",   String.valueOf(deliveryMinutes))
                    .tag("created_at",     p.getCreatedAt())
                    .register(registry);
        }

        log.debug("collectOrderInfoMetrics — registered {} order info gauges", orders.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inventory Metrics  (inventory_db)
    // ─────────────────────────────────────────────────────────────────────────

    private void collectInventoryMetrics() {
        double lowStock   = count("inventory_db", "stock",
                Criteria.where("availableQty").gt(0).lte(10));
        double outOfStock = count("inventory_db", "stock",
                Criteria.where("availableQty").is(0));

        Gauge.builder("blinkit_inventory_low_stock",    () -> lowStock)
                .description("Products with stock between 1 and 10").register(registry);
        Gauge.builder("blinkit_inventory_out_of_stock", () -> outOfStock)
                .description("Products with zero stock").register(registry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inventory Info Metrics  (per-product detail, read from InventoryInfoCache)
    //
    // Info metric pattern — value = 1, all fields as tags.
    // In Grafana → Table → labelsToFields → merge → one row per product.
    // ─────────────────────────────────────────────────────────────────────────

    private void collectInventoryInfoMetrics() {
        Map<String, InventoryInfoPayload> products = inventoryInfoCache.snapshot();

        for (InventoryInfoPayload p : products.values()) {
            final double availableQty = p.getAvailableQty();
            final double reservedQty  = p.getReservedQty();

            Gauge.builder("blinkit_inventory_info", () -> 1.0)
                    .description("Per-product inventory snapshot")
                    .tag("product_id",      p.getProductId())
                    .tag("product_name",    p.getProductName())
                    .tag("available_qty",      String.valueOf(p.getAvailableQty()))
                    .tag("reserved_qty",       String.valueOf(p.getReservedQty()))
                    .tag("stock_status",       p.getStockStatus())
                    .tag("low_stock_threshold",String.valueOf(p.getLowStockThreshold()))
                    .tag("is_available",       p.getIsAvailable())
                    .tag("last_restocked",     p.getLastRestocked())
                    .register(registry);
        }

        log.debug("collectInventoryInfoMetrics — registered {} product gauges", products.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Infrastructure Health Metrics  (read from InfraHealthCache, refreshed every 30min)
    //
    // Two metrics per component:
    //   blinkit_infra_status       — 1=RUNNING, 0=FAILED  (for stat/alert panels)
    //   blinkit_infra_response_ms  — response time of the last health check
    //
    // Info metric:
    //   blinkit_infra_info         — value=1, tags: component, status, last_checked, error
    //   → drives the Grafana table with color-coded status column
    // ─────────────────────────────────────────────────────────────────────────

    private void collectInfraHealthMetrics() {
        Map<String, InfraHealthPayload> infra = infraHealthCache.snapshot();

        for (InfraHealthPayload p : infra.values()) {
            final double statusValue = "RUNNING".equals(p.getStatus()) ? 1.0 : 0.0;
            final double responseMs  = p.getResponseTimeMs();

            // Numeric gauge — used for stat panels and alerting rules
            Gauge.builder("blinkit_infra_status", () -> statusValue)
                    .description("Infrastructure component status: 1=RUNNING 0=FAILED")
                    .tag("component", p.getComponent())
                    .register(registry);

            Gauge.builder("blinkit_infra_response_ms", () -> responseMs)
                    .description("Last health check response time in milliseconds")
                    .tag("component", p.getComponent())
                    .register(registry);

            // Info metric — drives the Grafana table (status, response time, last checked, error)
            Gauge.builder("blinkit_infra_info", () -> 1.0)
                    .description("Infrastructure component health snapshot")
                    .tag("component",    p.getComponent())
                    .tag("status",       p.getStatus())
                    .tag("response_ms",  String.valueOf(p.getResponseTimeMs()))
                    .tag("last_checked", p.getLastChecked())
                    .tag("error",        p.getErrorMessage())
                    .register(registry);
        }

        log.debug("collectInfraHealthMetrics — {} components checked", infra.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helper — queries any database + collection
    // ─────────────────────────────────────────────────────────────────────────

    private double count(String database, String collection, Criteria criteria) {
        try {
            Document filter = Document.parse(criteria.getCriteriaObject().toJson());
            return mongoClient.getDatabase(database)
                    .getCollection(collection)
                    .countDocuments(filter);
        } catch (Exception e) {
            log.error("Failed to count {}.{}: {}", database, collection, e.getMessage());
            return -1;
        }
    }
}
