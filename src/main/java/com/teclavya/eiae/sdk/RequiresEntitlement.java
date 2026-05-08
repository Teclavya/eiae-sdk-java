package com.teclavya.eiae.sdk;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated method requires the caller to have a specific
 * entitlement feature enabled.
 *
 * <p>The EIAE SDK AOP aspect {@link RequiresEntitlementAspect} intercepts calls
 * to annotated methods and throws {@link EntitlementMissingException} if the
 * resolved user does not have the required feature.
 *
 * <p>Example usage:
 * <pre>{@code
 * @GetMapping("/interviews")
 * @RequiresEntitlement("mock_interviews")
 * public ResponseEntity<List<Interview>> listInterviews() { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresEntitlement {

    /**
     * The feature key that must be present in the user's entitlement snapshot.
     * Example: {@code "mock_interviews"}, {@code "ai_resume_builder"}.
     */
    String value();
}
