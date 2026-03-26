package com.blinkit.metricsexporter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MetricsExporterApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetricsExporterApplication.class, args);
    }
}
