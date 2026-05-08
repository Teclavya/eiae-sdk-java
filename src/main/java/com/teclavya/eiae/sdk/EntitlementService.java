package com.teclavya.eiae.sdk;

/**
 * Primary entry point for entitlement checks in consuming services.
 *
 * <p>Consumers typically use the annotations {@link RequiresEntitlement} and
 * {@link RequiresQuota} instead of calling this interface directly. Use this
 * interface when programmatic control is needed (e.g., feature flags inside
 * conditional logic).
 *
 * <p>Implementations are thread-safe and cache-backed.
 */
public interface EntitlementService {

    /**
     * Returns {@code true} if {@code userId} has the given {@code feature} enabled
     * in their current entitlement snapshot.
     *
     * @param userId  the authenticated user identifier
     * @param feature the feature key (e.g., {@code "mock_interviews"})
     * @return {@code true} if the feature is enabled
     */
    boolean has(String userId, String feature);

    /**
     * Attempts to consume {@code amount} units of quota for {@code feature} on
     * behalf of {@code userId}. Uses {@code idempotencyKey} to deduplicate retries.
     *
     * @param userId          the authenticated user identifier
     * @param feature         the feature quota key
     * @param amount          units to consume (≥ 1)
     * @param idempotencyKey  client-supplied idempotency key (UUID recommended)
     * @return {@link QuotaResult} with outcome ALLOWED or DENIED
     */
    QuotaResult consumeQuota(String userId, String feature, int amount, String idempotencyKey);

    /**
     * Returns the full entitlement snapshot for {@code userId}, fetching from
     * EIAE if not cached.
     *
     * @param userId the authenticated user identifier
     * @return the current {@link EntitlementsSnapshot}
     */
    EntitlementsSnapshot getSnapshot(String userId);

    /**
     * Invalidates the in-process cache entry for {@code userId}.
     * The next call to {@link #has} or {@link #getSnapshot} will fetch fresh data
     * from EIAE.
     *
     * @param userId the authenticated user identifier
     */
    void invalidateCache(String userId);
}
