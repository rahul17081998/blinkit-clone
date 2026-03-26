package com.blinkit.metricsexporter.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Exposes delivery partner availability counts as Prometheus gauges.
 * Queries delivery_db.delivery_partners every 30s.
 *
 * Metrics exposed:
 *   blinkit_delivery_partners_available   — isAvailable=true  AND isActive=true
 *   blinkit_delivery_partners_unavailable — isAvailable=false AND isActive=true
 *   blinkit_delivery_partners_total       — isActive=true (all active)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryPartnerMetrics {

    private final MongoTemplate mongoTemplate;
    private final MeterRegistry meterRegistry;

    private volatile double available   = 0;
    private volatile double unavailable = 0;
    private volatile double total       = 0;

    private static final String COLLECTION = "delivery_partners";

    @PostConstruct
    public void init() {
        Gauge.builder("blinkit.delivery.partners.available", this, m -> m.available)
                .description("Delivery partners currently available for assignment")
                .register(meterRegistry);

        Gauge.builder("blinkit.delivery.partners.unavailable", this, m -> m.unavailable)
                .description("Delivery partners currently on a delivery (unavailable)")
                .register(meterRegistry);

        Gauge.builder("blinkit.delivery.partners.total", this, m -> m.total)
                .description("Total active delivery partners registered")
                .register(meterRegistry);

        refresh(); // load immediately at startup
    }

    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
        try {
            available = mongoTemplate.count(
                    Query.query(Criteria.where("isAvailable").is(true).and("isActive").is(true)),
                    COLLECTION);

            unavailable = mongoTemplate.count(
                    Query.query(Criteria.where("isAvailable").is(false).and("isActive").is(true)),
                    COLLECTION);

            total = mongoTemplate.count(
                    Query.query(Criteria.where("isActive").is(true)),
                    COLLECTION);

            log.debug("Delivery partner metrics — available: {}, unavailable: {}, total: {}",
                    available, unavailable, total);

        } catch (Exception e) {
            log.error("Failed to refresh delivery partner metrics: {}", e.getMessage());
        }
    }
}
