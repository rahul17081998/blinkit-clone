package com.blinkit.metricsexporter.config;

import com.mongodb.MongoClientSettings;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Reduces MongoDB connection timeouts so that if MongoDB is unreachable,
 * the metrics-exporter still starts quickly instead of blocking for 30s per cache.
 *
 * Default MongoDB driver timeouts:
 *   serverSelectionTimeout = 30,000ms  ← blocks @PostConstruct refresh() for 30s if Mongo is down
 *   connectTimeout         = 10,000ms
 *   socketTimeout          = 0ms (infinite)
 *
 * With these settings, a down MongoDB is detected in ≤ 3s and the exception
 * is caught by the try/catch in each cache's refresh() method.
 * The service starts normally; infra metrics show MONGODB=FAILED.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoTimeoutCustomizer() {
        return builder -> builder
                .applyToClusterSettings(cluster ->
                        cluster.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToSocketSettings(socket ->
                        socket.connectTimeout(3, TimeUnit.SECONDS)
                              .readTimeout(10, TimeUnit.SECONDS));
    }
}
