package com.blinkit.metricsexporter.config;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    /**
     * A dedicated Prometheus registry used ONLY for custom business metrics.
     *
     * Why not use the auto-configured global MeterRegistry?
     *   - MetricsService calls registry.clear() before each scrape to reset values.
     *   - Clearing the global registry would wipe Spring Boot's built-in metrics
     *     (JVM heap, GC, HTTP request counts, etc.).
     *   - This isolated registry is safe to clear — it holds only what MetricsService registers.
     */
    @Bean("businessMetricsRegistry")
    public PrometheusMeterRegistry businessMetricsRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
}
