package com.teclavya.eiae.sdk;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of {@link EntitlementService}.
 *
 * <p>Uses a Caffeine in-process cache to avoid round-trips to EIAE on every
 * request. Cache TTL is controlled by {@link EiaeProperties#getCacheTtlSeconds()}.
 *
 * <p>When {@link EiaeProperties#isLocalOverride()} is {@code true}, all
 * {@code has()} calls return {@code true} and all {@code consumeQuota()} calls
 * return ALLOWED — no HTTP calls are made. Intended for local development only.
 */
public class EntitlementServiceImpl implements EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementServiceImpl.class);

    private final EiaeProperties properties;
    private final RestClient restClient;
    private final Cache<String, EntitlementsSnapshot> cache;

    public EntitlementServiceImpl(EiaeProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getApiBase())
                .defaultHeader("X-Internal-Token", nullToEmpty(properties.getInternalToken()))
                .defaultHeader("Authorization", "Bearer " + nullToEmpty(properties.getInternalToken()))
                .build();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(10_000)
                .build();
    }

    /**
     * Constructor for testing — accepts a pre-built RestClient.
     */
    EntitlementServiceImpl(EiaeProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(10_000)
                .build();
    }

    @Override
    public boolean has(String userId, String feature) {
        if (properties.isLocalOverride()) {
            log.debug("[EIAE local-override] has({}, {}) => true", userId, feature);
            return true;
        }
        EntitlementsSnapshot snapshot = getSnapshot(userId);
        return snapshot.features() != null && snapshot.features().contains(feature);
    }

    @Override
    public QuotaResult consumeQuota(String userId, String feature, int amount, String idempotencyKey) {
        if (properties.isLocalOverride()) {
            log.debug("[EIAE local-override] consumeQuota({}, {}) => ALLOWED", userId, feature);
            return new QuotaResult(QuotaResult.ALLOWED, amount, Integer.MAX_VALUE, null);
        }

        try {
            QuotaResult result = restClient.post()
                    .uri("/v1/entitlements/consume-quota")
                    .header("X-Idempotency-Key", idempotencyKey)
                    .body(new ConsumeQuotaRequest(userId, feature, amount, idempotencyKey))
                    .retrieve()
                    .body(QuotaResult.class);

            // Invalidate snapshot cache so next has() reflects new quota state
            invalidateCache(userId);
            return result != null ? result : deniedResult();
        } catch (Exception ex) {
            log.error("[EIAE SDK] consumeQuota failed for userId={} feature={}: {}", userId, feature, ex.getMessage());
            // Fail-open: return DENIED to prevent silent over-consumption
            return deniedResult();
        }
    }

    @Override
    public EntitlementsSnapshot getSnapshot(String userId) {
        return cache.get(userId, this::fetchFromEiae);
    }

    @Override
    public void invalidateCache(String userId) {
        cache.invalidate(userId);
        log.debug("[EIAE SDK] Cache invalidated for userId={}", userId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private EntitlementsSnapshot fetchFromEiae(String userId) {
        try {
            log.debug("[EIAE SDK] Fetching entitlements from EIAE for userId={}", userId);
            EntitlementsSnapshot snapshot = restClient.get()
                    .uri("/v1/entitlements/{userId}", userId)
                    .retrieve()
                    .body(EntitlementsSnapshot.class);
            return snapshot != null ? snapshot : emptySnapshot();
        } catch (Exception ex) {
            log.error("[EIAE SDK] Failed to fetch entitlements for userId={}: {}", userId, ex.getMessage());
            // Fail-safe: return empty snapshot (no features enabled)
            return emptySnapshot();
        }
    }

    private EntitlementsSnapshot emptySnapshot() {
        return new EntitlementsSnapshot(List.of(), Map.of(), List.of(), Instant.now());
    }

    private QuotaResult deniedResult() {
        return new QuotaResult(QuotaResult.DENIED, 0, 0, null);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    /** Internal DTO for the consume-quota POST body */
    private record ConsumeQuotaRequest(String userId, String feature, int amount, String idempotencyKey) {}
}
