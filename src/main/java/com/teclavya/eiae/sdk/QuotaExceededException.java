package com.teclavya.eiae.sdk;

import java.time.Instant;

/**
 * Thrown by {@link RequiresQuotaAspect} when a user's quota for a feature is
 * exhausted and the EIAE platform returns DENIED.
 *
 * <p>Consuming services should map this exception to HTTP 429 Too Many Requests.
 * Include the {@code periodResetsAt} value in the {@code Retry-After} header.
 * Example:
 * <pre>{@code
 * @ExceptionHandler(QuotaExceededException.class)
 * public ResponseEntity<ErrorBody> handle(QuotaExceededException ex) {
 *     return ResponseEntity.status(429)
 *         .header("Retry-After", ex.getPeriodResetsAt().toString())
 *         .body(new ErrorBody("QUOTA_EXCEEDED", ex.getMessage()));
 * }
 * }</pre>
 */
public class QuotaExceededException extends RuntimeException {

    private final String feature;
    private final String userId;
    private final Instant periodResetsAt;

    public QuotaExceededException(String feature, String userId, Instant periodResetsAt) {
        super(String.format(
                "Quota exceeded for user '%s' on feature '%s'. Resets at: %s",
                userId, feature, periodResetsAt));
        this.feature = feature;
        this.userId = userId;
        this.periodResetsAt = periodResetsAt;
    }

    public String getFeature() {
        return feature;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getPeriodResetsAt() {
        return periodResetsAt;
    }
}
