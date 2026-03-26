package com.blinkit.metricsexporter.cache;

import com.blinkit.metricsexporter.dto.InventoryInfoPayload;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryInfoCache {

    private final MongoClient mongoClient;

    private final ConcurrentHashMap<String, InventoryInfoPayload> cache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    @PostConstruct
    public void init() { refresh(); }

    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
        try {
            // ── Query 1: get all productIds where isAvailable=false from product_db ──
            // One batch query — only fetch the productId field (projection)
            Set<String> unavailableProductIds = fetchUnavailableProductIds();

            // ── Query 2: filter stock at MongoDB level — no Java-side filtering ──
            //
            // Matches documents where ANY of:
            //   a) availableQty == 0                          → OUT_OF_STOCK
            //   b) 0 < availableQty <= lowStockThreshold      → LOW_STOCK
            //      ($ifNull handles null threshold → defaults to 10)
            //   c) productId is in the unavailable set        → admin disabled
            //
            List<Bson> orClauses = new ArrayList<>();

            // (a) out of stock
            orClauses.add(Filters.eq("availableQty", 0));

            // (b) low stock — compare two fields in the same document using $expr
            orClauses.add(new Document("$expr", new Document("$and", Arrays.asList(
                    new Document("$gt",  Arrays.asList("$availableQty", 0)),
                    new Document("$lte", Arrays.asList(
                            "$availableQty",
                            new Document("$ifNull", Arrays.asList("$lowStockThreshold", DEFAULT_LOW_STOCK_THRESHOLD))
                    ))
            ))));

            // (c) admin-disabled products
            if (!unavailableProductIds.isEmpty()) {
                orClauses.add(Filters.in("productId", unavailableProductIds));
            }

            Bson filter = Filters.or(orClauses);

            ConcurrentHashMap<String, InventoryInfoPayload> fresh = new ConcurrentHashMap<>();

            for (Document stock : mongoClient.getDatabase("inventory_db")
                    .getCollection("stock")
                    .find(filter)) {

                String productId   = str(stock, "productId");
                String productName = str(stock, "productName");
                long availableQty  = num(stock, "availableQty");
                long reservedQty   = num(stock, "reservedQty");
                long threshold     = stock.getInteger("lowStockThreshold") != null
                        ? stock.getInteger("lowStockThreshold") : DEFAULT_LOW_STOCK_THRESHOLD;

                String stockStatus   = deriveStockStatus(availableQty, threshold);
                // isAvailable resolved from the pre-fetched set — no extra DB call
                String isAvailable   = unavailableProductIds.contains(productId) ? "false" : "true";
                String lastRestocked = formatInstant(stock.get("lastRestockedAt"));

                fresh.put(productId, InventoryInfoPayload.builder()
                        .productId(productId)
                        .productName(productName)
                        .availableQty(availableQty)
                        .reservedQty(reservedQty)
                        .stockStatus(stockStatus)
                        .lowStockThreshold(threshold)
                        .isAvailable(isAvailable)
                        .lastRestocked(lastRestocked)
                        .build());
            }

            cache.clear();
            cache.putAll(fresh);
            log.debug("InventoryInfoCache refreshed — {} products need attention (unavailableIds={})",
                    cache.size(), unavailableProductIds.size());

        } catch (Exception e) {
            log.error("Failed to refresh InventoryInfoCache: {}", e.getMessage());
        }
    }

    public Map<String, InventoryInfoPayload> snapshot() {
        return Collections.unmodifiableMap(cache);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * One query to product_db — fetches only productId field (projection).
     * Returns a Set for O(1) lookup when building payloads.
     */
    private Set<String> fetchUnavailableProductIds() {
        Set<String> ids = new HashSet<>();
        try {
            mongoClient.getDatabase("product_db")
                    .getCollection("products")
                    .find(Filters.eq("isAvailable", false))
                    .projection(Projections.fields(
                            Projections.include("productId"),
                            Projections.excludeId()
                    ))
                    .forEach(doc -> {
                        String id = doc.getString("productId");
                        if (id != null && !id.isBlank()) ids.add(id);
                    });
        } catch (Exception e) {
            log.warn("Could not fetch unavailable productIds: {}", e.getMessage());
        }
        return ids;
    }

    private String deriveStockStatus(long availableQty, long threshold) {
        if (availableQty == 0)         return "OUT_OF_STOCK";
        if (availableQty <= threshold) return "LOW_STOCK";
        return "IN_STOCK";
    }

    private String str(Document doc, String field) {
        String val = doc.getString(field);
        return (val != null && !val.isBlank()) ? val : "NA";
    }

    private long num(Document doc, String field) {
        Integer val = doc.getInteger(field);
        return val != null ? val : 0;
    }

    private String formatInstant(Object value) {
        Instant instant = null;
        if (value instanceof Date)    instant = ((Date) value).toInstant();
        if (value instanceof Instant) instant = (Instant) value;
        return instant != null ? FORMATTER.format(instant) : "NA";
    }
}
