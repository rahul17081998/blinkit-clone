package com.blinkit.metricsexporter.controller;

import com.blinkit.metricsexporter.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single endpoint scraped by Prometheus every 15s.
 *
 * Prometheus config:
 *   metrics_path: /metrics
 *   targets: ['host.docker.internal:8092']
 *
 * To add a new metric group → add a method in MetricsService only.
 * This controller never changes.
 */
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping(produces = "text/plain;version=0.0.4;charset=utf-8")
    public String getMetrics() {
        return metricsService.collectAllMetrics();
    }
}
