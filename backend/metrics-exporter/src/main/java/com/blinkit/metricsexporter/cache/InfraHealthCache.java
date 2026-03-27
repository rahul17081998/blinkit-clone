package com.blinkit.metricsexporter.cache;

import com.blinkit.metricsexporter.dto.InfraHealthPayload;
import com.mongodb.client.MongoClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Runs active health checks against each infrastructure component every 30 minutes.
 *
 * Tests mirror infra-check.sh:
 *   MONGODB — insert doc → read back → verify → delete
 *   REDIS   — SET key → GET → verify value → DELETE
 *   KAFKA   — AdminClient.listTopics() with 5s timeout
 *   CDN     — HTTP GET to Cloudinary ping endpoint (authenticated)
 *
 * Results stored as InfraHealthPayload: status=RUNNING|FAILED, responseTimeMs, lastChecked, errorMessage.
 * An OVERALL entry is also stored: RUNNING only if all 4 components pass.
 *
 * MetricsService reads a snapshot on every Prometheus scrape.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfraHealthCache {

    private final MongoClient mongoClient;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrapServers;

    @Value("${cloudinary.cloud-name:unknown}")
    private String cloudinaryCloudName;

    @Value("${cloudinary.api-key:}")
    private String cloudinaryApiKey;

    @Value("${cloudinary.api-secret:}")
    private String cloudinaryApiSecret;

    @Value("${infra.health.check.interval-ms:1800000}")
    private long intervalMs;

    private static final String TEST_DB         = "infra_health_check_db";
    private static final String TEST_COLLECTION = "health_probe";
    private static final String REDIS_TEST_KEY  = "infra:health:probe";
    private static final String REDIS_TEST_VAL  = "blinkit-health-ok";
    private static final int    CONNECT_TIMEOUT = 5_000;
    private static final int    KAFKA_TIMEOUT   = 5;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    // key = component name (MONGODB | REDIS | KAFKA | CDN | OVERALL)
    private final ConcurrentHashMap<String, InfraHealthPayload> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() { refresh(); }

    // Interval driven by INFRA_HEALTH_CHECK_INTERVAL_MS env var (default 30 min)
    @Scheduled(fixedDelayString = "${infra.health.check.interval-ms:1800000}")
    public void refresh() {
        log.info("InfraHealthCache — starting health checks");

        InfraHealthPayload mongo = checkMongoDB();
        InfraHealthPayload redis = checkRedis();
        InfraHealthPayload kafka = checkKafka();
        InfraHealthPayload cdn   = checkCDN();

        // Overall: RUNNING only when all four pass
        boolean allOk = isRunning(mongo) && isRunning(redis) && isRunning(kafka) && isRunning(cdn);
        InfraHealthPayload overall = InfraHealthPayload.builder()
                .component("OVERALL")
                .status(allOk ? "RUNNING" : "FAILED")
                .responseTimeMs(mongo.getResponseTimeMs() + redis.getResponseTimeMs()
                        + kafka.getResponseTimeMs() + cdn.getResponseTimeMs())
                .lastChecked(FORMATTER.format(Instant.now()))
                .errorMessage(allOk ? "none" : buildFailedList(mongo, redis, kafka, cdn))
                .build();

        // Compute next scheduled check time (now + interval)
        String nextScheduled = FORMATTER.format(Instant.now().plusMillis(intervalMs));
        mongo.setNextScheduled(nextScheduled);
        redis.setNextScheduled(nextScheduled);
        kafka.setNextScheduled(nextScheduled);
        cdn.setNextScheduled(nextScheduled);
        overall.setNextScheduled(nextScheduled);

        cache.put("MONGODB", mongo);
        cache.put("REDIS",   redis);
        cache.put("KAFKA",   kafka);
        cache.put("CDN",     cdn);
        cache.put("OVERALL", overall);

        log.info("InfraHealthCache — OVERALL={} [MONGODB={}, REDIS={}, KAFKA={}, CDN={}]",
                overall.getStatus(), mongo.getStatus(), redis.getStatus(),
                kafka.getStatus(), cdn.getStatus());
    }

    public Map<String, InfraHealthPayload> snapshot() {
        return Collections.unmodifiableMap(cache);
    }

    // ── Checks ───────────────────────────────────────────────────────────────

    /**
     * MongoDB: insert a probe document → read it back → verify → delete.
     * All 3 sub-steps must pass for RUNNING (mirrors infra-check.sh).
     */
    private InfraHealthPayload checkMongoDB() {
        long start = System.currentTimeMillis();
        String probeId = "health_probe_" + System.currentTimeMillis();
        try {
            var col = mongoClient.getDatabase(TEST_DB).getCollection(TEST_COLLECTION);

            // 1. Insert
            col.insertOne(new Document("_id", probeId).append("status", "ok"));

            // 2. Read back and verify
            Document found = col.find(new Document("_id", probeId)).first();
            if (found == null || !"ok".equals(found.getString("status"))) {
                throw new RuntimeException("Read-back verification failed");
            }

            // 3. Delete
            long deleted = col.deleteOne(new Document("_id", probeId)).getDeletedCount();
            if (deleted != 1) throw new RuntimeException("Delete probe doc failed");

            return running("MONGODB", start);
        } catch (Exception e) {
            // Best-effort cleanup
            try { mongoClient.getDatabase(TEST_DB).getCollection(TEST_COLLECTION)
                    .deleteOne(new Document("_id", probeId)); } catch (Exception ignored) {}
            return failed("MONGODB", start, e);
        }
    }

    /**
     * Redis: SET probe key → GET → verify value → DELETE.
     * Mirrors infra-check.sh Redis block.
     */
    private InfraHealthPayload checkRedis() {
        long start = System.currentTimeMillis();
        try (Jedis jedis = buildJedis()) {
            // 1. SET with 60s TTL
            String setResult = jedis.setex(REDIS_TEST_KEY, 60, REDIS_TEST_VAL);
            if (!"OK".equalsIgnoreCase(setResult)) throw new RuntimeException("SET returned: " + setResult);

            // 2. GET and verify
            String got = jedis.get(REDIS_TEST_KEY);
            if (!REDIS_TEST_VAL.equals(got)) throw new RuntimeException("GET mismatch: got=" + got);

            // 3. DELETE
            jedis.del(REDIS_TEST_KEY);

            return running("REDIS", start);
        } catch (Exception e) {
            return failed("REDIS", start, e);
        }
    }

    /**
     * Kafka: AdminClient.listTopics() with 5s timeout.
     */
    private InfraHealthPayload checkKafka() {
        long start = System.currentTimeMillis();
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(KAFKA_TIMEOUT * 1_000));
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(KAFKA_TIMEOUT * 1_000));
        try (AdminClient admin = AdminClient.create(props)) {
            Set<String> topics = admin.listTopics().names().get(KAFKA_TIMEOUT, TimeUnit.SECONDS);
            log.debug("Kafka health — {} topics visible", topics.size());
            return running("KAFKA", start);
        } catch (Exception e) {
            return failed("KAFKA", start, e);
        }
    }

    /**
     * CDN (Cloudinary): HTTP GET /ping endpoint.
     * If API credentials are configured, use authenticated /usage (more thorough).
     * Falls back to unauthenticated /ping if credentials are absent.
     */
    private InfraHealthPayload checkCDN() {
        long start = System.currentTimeMillis();
        try {
            String endpoint;
            String authHeader = null;

            if (!cloudinaryApiKey.isBlank() && !cloudinaryApiSecret.isBlank()) {
                // Authenticated check — validates cloud name + credentials
                endpoint   = "https://api.cloudinary.com/v1_1/" + cloudinaryCloudName + "/ping";
                String creds = cloudinaryApiKey + ":" + cloudinaryApiSecret;
                authHeader = "Basic " + Base64.getEncoder().encodeToString(creds.getBytes());
            } else {
                // Unauthenticated connectivity check only
                endpoint = "https://api.cloudinary.com/v1_1/" + cloudinaryCloudName + "/ping";
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(CONNECT_TIMEOUT);
            conn.setRequestMethod("GET");
            if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);

            int code = conn.getResponseCode();
            conn.disconnect();

            if (code == 401) throw new RuntimeException("HTTP 401 — invalid Cloudinary credentials");
            if (code < 200 || code >= 400) throw new RuntimeException("HTTP " + code);

            return running("CDN", start);
        } catch (Exception e) {
            return failed("CDN", start, e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Jedis buildJedis() {
        // Use SSL for remote hosts (e.g. Upstash) — plain socket for localhost (dev)
        boolean useSsl = !redisHost.equals("localhost") && !redisHost.equals("127.0.0.1");
        Jedis jedis = new Jedis(redisHost, redisPort, CONNECT_TIMEOUT, useSsl);
        if (redisPassword != null && !redisPassword.isBlank()) {
            jedis.auth(redisPassword);
        }
        return jedis;
    }

    private boolean isRunning(InfraHealthPayload p) {
        return "RUNNING".equals(p.getStatus());
    }

    private String buildFailedList(InfraHealthPayload... payloads) {
        StringBuilder sb = new StringBuilder();
        for (InfraHealthPayload p : payloads) {
            if (!isRunning(p)) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(p.getComponent()).append(": ").append(p.getErrorMessage());
            }
        }
        return sb.length() > 0 ? sb.toString() : "none";
    }

    private InfraHealthPayload running(String component, long startMs) {
        long elapsed = System.currentTimeMillis() - startMs;
        log.debug("InfraCheck {} — RUNNING ({}ms)", component, elapsed);
        return InfraHealthPayload.builder()
                .component(component)
                .status("RUNNING")
                .responseTimeMs(elapsed)
                .lastChecked(FORMATTER.format(Instant.now()))
                .errorMessage("none")
                .build();
    }

    private InfraHealthPayload failed(String component, long startMs, Exception e) {
        long elapsed = System.currentTimeMillis() - startMs;
        String msg = e.getMessage() != null
                ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 100))
                : "unknown error";
        log.warn("InfraCheck {} — FAILED ({}ms): {}", component, elapsed, msg);
        return InfraHealthPayload.builder()
                .component(component)
                .status("FAILED")
                .responseTimeMs(elapsed)
                .lastChecked(FORMATTER.format(Instant.now()))
                .errorMessage(msg)
                .build();
    }
}
