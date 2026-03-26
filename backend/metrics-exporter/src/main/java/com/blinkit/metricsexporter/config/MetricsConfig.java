package com.blinkit.metricsexporter.config;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MetricsConfig {

    /**
     * Primary Prometheus registry — used by Spring Boot Actuator's /actuator/prometheus endpoint.
     *
     * Root cause of empty /actuator/prometheus:
     *   Spring Boot's PrometheusScrapeEndpoint is wired to the Spring-managed
     *   io.prometheus.metrics.model.registry.PrometheusRegistry bean (created by auto-config).
     *   If we create PrometheusMeterRegistry with the no-arg constructor, it creates its OWN
     *   internal PrometheusRegistry — a different instance than what the scrape endpoint uses.
     *   Metrics registered in our registry never appear in the scrape.
     *
     * Fix: accept the Spring-managed PrometheusRegistry as a parameter and pass it to the
     *   PrometheusMeterRegistry constructor so both our registry and the scrape endpoint share
     *   the same underlying PrometheusRegistry.
     *
     * JVM metrics are explicitly bound here because Spring Boot's MeterBinder auto-config
     * backs off when it finds a custom PrometheusMeterRegistry bean.
     *
     * This registry is NEVER cleared — it holds JVM/process metrics permanently.
     * Scraped by Prometheus job: metrics-exporter-jvm → /actuator/prometheus
     */
    @Primary
    @Bean("jvmMetricsRegistry")
    public PrometheusMeterRegistry jvmMetricsRegistry(PrometheusRegistry prometheusRegistry, Clock clock) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, prometheusRegistry, clock);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
        return registry;
    }

    /**
     * A secondary, isolated Prometheus registry used ONLY for custom business metrics.
     *
     * Why a separate registry?
     *   - MetricsService calls registry.clear() before each scrape to reset gauge values.
     *   - Clearing the @Primary registry would wipe JVM metrics.
     *   - This isolated registry is safe to clear — it holds only what MetricsService registers.
     *
     * Scraped by Prometheus job: metrics-exporter → /metrics
     */
    @Bean("businessMetricsRegistry")
    public PrometheusMeterRegistry businessMetricsRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
}
