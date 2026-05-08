package com.teclavya.eiae.sdk;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated method consumes quota for a specific feature.
 *
 * <p>The EIAE SDK AOP aspect {@link RequiresQuotaAspect} intercepts the call,
 * invokes {@link EntitlementService#consumeQuota}, and throws
 * {@link QuotaExceededException} if the outcome is DENIED.
 *
 * <p>An idempotency key is read from the HTTP request header specified by
 * {@link #idempotencyKeyHeader()}. If no header is present, a random UUID is
 * generated for the attempt.
 *
 * <p>Example usage:
 * <pre>{@code
 * @PostMapping("/interviews")
 * @RequiresQuota(value = "mock_interviews", amount = 1)
 * public ResponseEntity<Interview> startInterview(@RequestBody ...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresQuota {

    /**
     * The feature quota key.
     * Example: {@code "mock_interviews"}, {@code "ai_resume_credits"}.
     */
    String value();

    /**
     * Number of quota units to consume per invocation.
     * Default: 1.
     */
    int amount() default 1;

    /**
     * Name of the HTTP request header from which to read the idempotency key.
     * Default: {@code "X-Idempotency-Key"}.
     */
    String idempotencyKeyHeader() default "X-Idempotency-Key";
}
