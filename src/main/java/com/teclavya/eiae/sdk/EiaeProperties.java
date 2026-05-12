package com.teclavya.eiae.sdk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the EIAE SDK.
 * Bind via {@code eiae.*} in application.yml / application.properties.
 *
 * <p>Example:
 * <pre>
 * eiae:
 *   api-base: http://eiae-platform-service:8089
 *   enforce-entitlements: true
 *   local-override: false
 *   cache-ttl-seconds: 60
 *   internal-token: ${EIAE_INTERNAL_TOKEN}
 * </pre>
 */
@ConfigurationProperties("eiae")
@Data
public class EiaeProperties {

    /**
     * Base URL of the EIAE platform service.
     * Default: http://eiae-platform-service:8089 (K8s internal DNS)
     */
    private String apiBase = "http://eiae-platform-service:8089";

    /**
     * Preferred property alias for rollout clarity.
     *
     * <p>When set, this takes precedence over {@code localOverride} and is
     * interpreted as the inverse of local override behavior:
     * {@code enforceEntitlements=true => localOverride=false}.
     */
    private Boolean enforceEntitlements;

    /**
     * When true, all {@code has()} calls return {@code true} and all
     * {@code consumeQuota()} calls return ALLOWED without hitting EIAE.
     * Use for local development — set via env var {@code EIAE_LOCAL_OVERRIDE=true}.
     */
    private boolean localOverride = false;

    /**
     * Caffeine cache TTL in seconds for entitlement snapshots.
     * Default: 60 seconds.
     */
    private int cacheTtlSeconds = 60;

    /**
     * Internal service token sent as {@code X-Internal-Token} header on all
     * SDK-to-EIAE calls. Set via env var {@code EIAE_INTERNAL_TOKEN}.
     */
    private String internalToken;

    /**
     * Effective local override. If {@code enforceEntitlements} is explicitly
     * configured, it wins and is inverted to preserve backward compatibility.
     */
    public boolean isLocalOverride() {
        if (enforceEntitlements != null) {
            return !enforceEntitlements;
        }
        return localOverride;
    }
}
