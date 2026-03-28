package com.blinkit.metricsexporter.cache;

import com.blinkit.metricsexporter.dto.OrderInfoPayload;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains an in-memory snapshot of recent order details.
 *
 * Refresh cycle (every 30s):
 *   1. Fetch last 50 orders from order_db.orders
 *   2. For each order → look up payment status in payment_db.transactions
 *   3. For each order → look up delivery status in delivery_db.delivery_tasks
 *   4. Compute deliveryMinutes
 *   5. Store as OrderInfoPayload in ConcurrentHashMap keyed by orderId
 *
 * MetricsService reads a snapshot of this map on every Prometheus scrape (every 15s).
 * Because the map is refreshed every 30s and scraped every 15s, Prometheus always
 * gets the latest data without hitting MongoDB on every single scrape.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderInfoCache {

    private final MongoClient mongoClient;

    // key = orderId, value = full order snapshot
    private final ConcurrentHashMap<String, OrderInfoPayload> cache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    @PostConstruct
    public void init() {
        refresh();   // populate immediately at startup so first scrape has real data
    }

    @Scheduled(fixedDelay = 15_000)
    public void refresh() {
        try {
            ConcurrentHashMap<String, OrderInfoPayload> fresh = new ConcurrentHashMap<>();

            MongoCollection<Document> orders = mongoClient
                    .getDatabase("order_db")
                    .getCollection("orders");

            // Orders created in the last 12 hours, newest first
            Instant twelveHoursAgo = Instant.now().minus(12, ChronoUnit.HOURS);
            Document filter = new Document("createdAt",
                    new Document("$gte", Date.from(twelveHoursAgo)));

            for (Document order : orders.find(filter)
                    .sort(new Document("createdAt", -1))) {

                String orderId     = order.getString("orderId");
                String orderNumber = order.getString("orderNumber");
                String userId      = order.getString("userId");
                String orderStatus = orderStatus(order);
                double totalAmount = order.getDouble("totalAmount") != null
                        ? order.getDouble("totalAmount") : 0.0;
                Instant createdAt  = toInstant(order.get("createdAt"));

                String paymentStatus  = fetchPaymentStatus(orderId);
                String deliveryStatus = "NO_TASK";
                long   deliveryMinutes = 0;

                Document task = fetchDeliveryTask(orderId);
                if (task != null) {
                    deliveryStatus  = task.getString("status") != null
                            ? task.getString("status") : "UNKNOWN";
                    deliveryMinutes = computeDeliveryMinutes(createdAt, task);
                }

                // Effective status: for in-progress orders, show the delivery task status
                // (QUEUED / ASSIGNED / PICKED_UP / OUT_FOR_DELIVERY) so Grafana reflects
                // real-time delivery progress instead of being stuck on CONFIRMED.
                // Terminal/error states (CANCELLED, PAYMENT_FAILED) always use order status.
                String effectiveStatus = orderStatus;
                if (!isTerminalOrderStatus(orderStatus) && !"NO_TASK".equals(deliveryStatus) && !"UNKNOWN".equals(deliveryStatus)) {
                    effectiveStatus = deliveryStatus;
                }

                String createdAtStr = createdAt != null ? FORMATTER.format(createdAt) : "unknown";

                fresh.put(orderId, OrderInfoPayload.builder()
                        .orderId(orderId)
                        .orderNumber(orderNumber != null ? orderNumber : orderId)
                        .userId(userId != null ? userId : "unknown")
                        .orderStatus(effectiveStatus)
                        .paymentStatus(paymentStatus)
                        .deliveryStatus(deliveryStatus)
                        .totalAmount(totalAmount)
                        .deliveryMinutes(deliveryMinutes)
                        .createdAt(createdAtStr)
                        .build());
            }

            cache.clear();
            cache.putAll(fresh);
            log.debug("OrderInfoCache refreshed — {} orders loaded", cache.size());

        } catch (Exception e) {
            log.error("Failed to refresh OrderInfoCache: {}", e.getMessage());
        }
    }

    /**
     * Returns an immutable snapshot of the current cache.
     * Called by MetricsService on every Prometheus scrape.
     */
    public Map<String, OrderInfoPayload> snapshot() {
        return Collections.unmodifiableMap(cache);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String orderStatus(Document order) {
        Object status = order.get("status");
        if (status == null) return "UNKNOWN";
        // status is stored as a String (enum name) in MongoDB
        return status.toString();
    }

    /** Finds the ORDER_PAYMENT transaction for this orderId and returns its status. */
    private String fetchPaymentStatus(String orderId) {
        if (orderId == null) return "NO_PAYMENT";
        try {
            Document tx = mongoClient
                    .getDatabase("payment_db")
                    .getCollection("transactions")
                    .find(new Document("orderId", orderId)
                            .append("reason", "ORDER_PAYMENT"))
                    .first();
            if (tx == null) return "NO_PAYMENT";
            String status = tx.getString("status");
            return status != null ? status : "UNKNOWN";
        } catch (Exception e) {
            log.warn("Could not fetch payment for orderId {}: {}", orderId, e.getMessage());
            return "ERROR";
        }
    }

    /** Finds the delivery_task for this orderId. */
    private Document fetchDeliveryTask(String orderId) {
        if (orderId == null) return null;
        try {
            return mongoClient
                    .getDatabase("delivery_db")
                    .getCollection("delivery_tasks")
                    .find(new Document("orderId", orderId))
                    .first();
        } catch (Exception e) {
            log.warn("Could not fetch delivery task for orderId {}: {}", orderId, e.getMessage());
            return null;
        }
    }

    /**
     * Calculates delivery time in minutes.
     *   - If actualDeliveryAt exists → use it  (actual end-to-end time)
     *   - Otherwise                  → use now  (ongoing delivery, elapsed time so far)
     */
    private long computeDeliveryMinutes(Instant orderCreatedAt, Document task) {
        if (orderCreatedAt == null) return 0;

        Instant end = toInstant(task.get("actualDeliveryAt"));
        if (end == null) end = Instant.now();   // still in progress

        long minutes = (end.toEpochMilli() - orderCreatedAt.toEpochMilli()) / 60_000;
        return Math.max(minutes, 0);
    }

    private Instant toInstant(Object value) {
        if (value == null)          return null;
        if (value instanceof Date)  return ((Date) value).toInstant();
        if (value instanceof Instant) return (Instant) value;
        return null;
    }

    /**
     * Terminal order statuses where delivery progression is irrelevant.
     * For these, always show the order's own status rather than delivery status.
     */
    private boolean isTerminalOrderStatus(String status) {
        return "CANCELLED".equals(status)
                || "PAYMENT_FAILED".equals(status)
                || "DELIVERED".equals(status)
                || "REFUNDED".equals(status);
    }
}
