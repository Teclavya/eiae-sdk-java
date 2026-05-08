package com.teclavya.eiae.sdk;

/**
 * Thrown by {@link RequiresEntitlementAspect} when a user attempts to invoke
 * a method that requires an entitlement feature they do not have.
 *
 * <p>Consuming services should map this exception to HTTP 403 Forbidden.
 * Example with Spring MVC:
 * <pre>{@code
 * @ExceptionHandler(EntitlementMissingException.class)
 * public ResponseEntity<ErrorBody> handle(EntitlementMissingException ex) {
 *     return ResponseEntity.status(403)
 *         .body(new ErrorBody("ENTITLEMENT_MISSING", ex.getMessage()));
 * }
 * }</pre>
 */
public class EntitlementMissingException extends RuntimeException {

    private final String feature;
    private final String userId;

    public EntitlementMissingException(String feature, String userId) {
        super(String.format("User '%s' does not have entitlement for feature '%s'", userId, feature));
        this.feature = feature;
        this.userId = userId;
    }

    public String getFeature() {
        return feature;
    }

    public String getUserId() {
        return userId;
    }
}
