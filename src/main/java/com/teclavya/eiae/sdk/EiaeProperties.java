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
     * Name of the HTTP request attribute holding the platform's UUID for the
     * current user, stashed by the consuming service's own auth filter (e.g.
     * {@code request.setAttribute("userId", <uuid>)}). Used by
     * {@link RequiresQuotaAspect} / {@link RequiresEntitlementAspect} to resolve
     * the EIAE-recognized user identifier — the Spring Security principal name
     * ({@code auth.getName()}) is frequently the user's email, not a UUID.
     * Default: {@code "userId"}.
     */
    private String userIdRequestAttribute = "userId";

    /**
     * When {@code true} (default), an EIAE error (5xx, timeout, connection
     * failure, or any non-402 non-2xx status) is treated as fail-OPEN — the SDK
     * returns ALLOWED rather than blocking the caller. Only an authoritative
     * 402 DENIED response fails closed. Set to {@code false} to fail-closed on
     * any EIAE error instead.
     */
    private boolean failOpenOnError = true;

    public void setEnforceEntitlements(Boolean enforceEntitlements) {
        this.enforceEntitlements = enforceEntitlements;
        if (enforceEntitlements != null) {
            this.localOverride = !enforceEntitlements;
        }
    }

    public Boolean getEnforceEntitlements() {
        if (enforceEntitlements != null) {
            return enforceEntitlements;
        }
        return !localOverride;
    }

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

    public void setLocalOverride(boolean localOverride) {
        this.localOverride = localOverride;
    }
}
