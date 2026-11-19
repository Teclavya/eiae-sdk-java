package com.teclavya.eiae.sdk;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        this(properties, restClientBuilder(properties).build());
    }

    /**
     * Builds the default {@link RestClient.Builder} used by the primary
     * constructor. Package-visible so tests can bind a {@code MockRestServiceServer}
     * to the exact same builder configuration (base URL + default headers) that
     * production uses.
     */
    static RestClient.Builder restClientBuilder(EiaeProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getApiBase())
                .defaultHeader("X-Internal-Token", nullToEmpty(properties.getInternalToken()));
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

        UUID externalUserId;
        try {
            externalUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            log.error("[EIAE SDK] consumeQuota: userId '{}' is not a valid UUID (feature={}); {}",
                    userId, feature, properties.isFailOpenOnError() ? "failing OPEN" : "failing CLOSED");
            return failOpenOrDenied(amount);
        }

        try {
            QuotaResult result = restClient.post()
                    .uri("/api/v1/quota/consume-by-external-id")
                    .header("X-Idempotency-Key", idempotencyKey)
                    .body(new ConsumeByExternalIdRequest(externalUserId, feature, amount, idempotencyKey))
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        if (status == 200 || status == 402) {
                            EiaeQuotaResponse body = res.bodyTo(EiaeQuotaResponse.class);
                            if (body == null) {
                                log.error("[EIAE SDK] consumeQuota: empty body from EIAE (status={}) userId={} feature={}",
                                        status, userId, feature);
                                return failOpenOrDenied(amount);
                            }
                            // Invalidate snapshot cache so next has() reflects new quota state
                            invalidateCache(userId);
                            return mapQuotaResponse(body);
                        }
                        log.error("[EIAE SDK] consumeQuota: unexpected status {} from EIAE for userId={} feature={}",
                                status, userId, feature);
                        return failOpenOrDenied(amount);
                    });
            return result != null ? result : failOpenOrDenied(amount);
        } catch (Exception ex) {
            log.error("[EIAE SDK] consumeQuota failed for userId={} feature={}: {}", userId, feature, ex.getMessage());
            return failOpenOrDenied(amount);
        }
    }

    @Override
    public QuotaResult refundQuota(String userId, String feature, int amount, String idempotencyKey) {
        if (properties.isLocalOverride()) {
            log.debug("[EIAE local-override] refundQuota({}, {}) => ALLOWED", userId, feature);
            return new QuotaResult(QuotaResult.ALLOWED, 0, Integer.MAX_VALUE, null);
        }

        UUID externalUserId;
        try {
            externalUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            log.error("[EIAE SDK] refundQuota: userId '{}' is not a valid UUID (feature={} idem={})",
                    userId, feature, idempotencyKey);
            return new QuotaResult(QuotaResult.DENIED, 0, 0, null);
        }

        try {
            QuotaResult result = restClient.post()
                    .uri("/api/v1/quota/refund-by-external-id")
                    .header("X-Idempotency-Key", idempotencyKey)
                    .body(new ConsumeByExternalIdRequest(externalUserId, feature, amount, idempotencyKey))
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        if (status == 200) {
                            EiaeQuotaResponse body = res.bodyTo(EiaeQuotaResponse.class);
                            invalidateCache(userId);
                            return body != null
                                    ? mapQuotaResponse(body)
                                    : new QuotaResult(QuotaResult.ALLOWED, 0, Integer.MAX_VALUE, null);
                        }
                        log.error("[EIAE SDK] refundQuota: unexpected status {} from EIAE for userId={} feature={} idem={}",
                                status, userId, feature, idempotencyKey);
                        return new QuotaResult(QuotaResult.DENIED, 0, 0, null);
                    });
            return result != null ? result : new QuotaResult(QuotaResult.DENIED, 0, 0, null);
        } catch (Exception ex) {
            log.error("[EIAE SDK] refundQuota failed for userId={} feature={} idem={}: {}",
                    userId, feature, idempotencyKey, ex.getMessage());
            return new QuotaResult(QuotaResult.DENIED, 0, 0, null);
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
            EiaeEntitlementsResponse response = restClient.get()
                    .uri("/api/v1/entitlements/by-external-id/{externalId}", userId)
                    .retrieve()
                    .body(EiaeEntitlementsResponse.class);
            return response != null ? mapEntitlementsResponse(response) : emptySnapshot();
        } catch (Exception ex) {
            log.error("[EIAE SDK] Failed to fetch entitlements for userId={}: {}", userId, ex.getMessage());
            // Fail-safe: return empty snapshot (no features enabled)
            return emptySnapshot();
        }
    }

    private EntitlementsSnapshot mapEntitlementsResponse(EiaeEntitlementsResponse response) {
        List<String> features = response.features() != null ? List.copyOf(response.features()) : List.of();

        Map<String, EntitlementsSnapshot.QuotaInfo> quotas = new LinkedHashMap<>();
        if (response.quotas() != null) {
            response.quotas().forEach((code, state) ->
                    quotas.put(code, new EntitlementsSnapshot.QuotaInfo(state.consumed(), state.max(), null)));
        }

        List<String> sourceOrgIds = response.sourceOrgs() != null
                ? response.sourceOrgs().stream()
                        .map(org -> String.valueOf(org.orgId()))
                        .toList()
                : List.of();

        return new EntitlementsSnapshot(features, Map.copyOf(quotas), sourceOrgIds, Instant.now());
    }

    private EntitlementsSnapshot emptySnapshot() {
        return new EntitlementsSnapshot(List.of(), Map.of(), List.of(), Instant.now());
    }

    /**
     * Maps the EIAE wire response ({@code ALLOWED}/{@code IDEMPOTENT}/{@code DENIED})
     * to the SDK's {@link QuotaResult}. An {@code IDEMPOTENT} replay (retry of an
     * already-processed request) is mapped to {@code ALLOWED} — it must NOT be
     * treated as a denial, or a legitimate retry would incorrectly throw
     * {@link QuotaExceededException}.
     */
    private QuotaResult mapQuotaResponse(EiaeQuotaResponse response) {
        String outcome = "DENIED".equals(response.outcome()) ? QuotaResult.DENIED : QuotaResult.ALLOWED;
        return new QuotaResult(outcome, response.consumed(), response.max(), null);
    }

    /**
     * The EIAE-error path (5xx, timeout, connection failure, or any unexpected
     * non-200/402 status). Honors {@link EiaeProperties#isFailOpenOnError()}:
     * {@code true} (default) returns ALLOWED so a transient EIAE outage does not
     * block every caller; {@code false} returns DENIED.
     */
    private QuotaResult failOpenOrDenied(int amount) {
        if (properties.isFailOpenOnError()) {
            return new QuotaResult(QuotaResult.ALLOWED, amount, Integer.MAX_VALUE, null);
        }
        return deniedResult();
    }

    private QuotaResult deniedResult() {
        return new QuotaResult(QuotaResult.DENIED, 0, 0, null);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
