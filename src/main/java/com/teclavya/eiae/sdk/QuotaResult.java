package com.teclavya.eiae.sdk;

import java.time.Instant;

/**
 * Result of a quota consumption attempt from the EIAE platform service.
 */
public record QuotaResult(
        String outcome,
        int newConsumed,
        int max,
        Instant periodResetsAt
) {
    /** outcome value indicating the request was allowed and quota consumed */
    public static final String ALLOWED = "ALLOWED";

    /** outcome value indicating quota is exhausted */
    public static final String DENIED = "DENIED";

    public boolean isAllowed() {
        return ALLOWED.equals(outcome);
    }
}
