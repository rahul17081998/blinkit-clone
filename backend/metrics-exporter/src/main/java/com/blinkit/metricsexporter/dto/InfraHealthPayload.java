package com.blinkit.metricsexporter.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InfraHealthPayload {

    String component;       // MONGODB | REDIS | KAFKA | CDN | OVERALL
    String status;          // RUNNING | FAILED
    long   responseTimeMs;  // how long the check took
    String lastChecked;     // "yyyy-MM-dd HH:mm" IST
    String nextScheduled;   // "yyyy-MM-dd HH:mm" IST — when next check will run
    String errorMessage;    // first 100 chars of exception message, or "none"
}
