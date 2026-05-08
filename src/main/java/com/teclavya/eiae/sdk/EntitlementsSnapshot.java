package com.teclavya.eiae.sdk;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a user's entitlements fetched from the EIAE platform service.
 * Cached in-process by {@link EntitlementServiceImpl}.
 */
public record EntitlementsSnapshot(
        List<String> features,
        Map<String, QuotaInfo> quotas,
        List<String> sourceOrgIds,
        Instant fetchedAt
) {
    /**
     * Per-feature quota state at snapshot time.
     */
    public record QuotaInfo(
            int consumed,
            int max,
            Instant periodResetsAt
    ) {}
}
