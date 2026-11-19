package com.teclavya.eiae.sdk;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * AOP aspect that enforces {@link RequiresQuota} on annotated methods.
 *
 * <p>Reads the idempotency key from the HTTP request header
 * ({@link RequiresQuota#idempotencyKeyHeader()}), falling back to a randomly
 * generated UUID if the header is absent. Then delegates to
 * {@link EntitlementService#consumeQuota}.
 *
 * <p>If the result is DENIED, throws {@link QuotaExceededException}.
 *
 * <p>Bean is created by {@link EiaeAutoConfiguration}.
 */
@Aspect
public class RequiresQuotaAspect {

    private static final Logger log = LoggerFactory.getLogger(RequiresQuotaAspect.class);

    private final EntitlementService entitlementService;
    private final EiaeProperties properties;

    public RequiresQuotaAspect(EntitlementService entitlementService, EiaeProperties properties) {
        this.entitlementService = entitlementService;
        this.properties = properties;
    }

    /**
     * Backward-compatible constructor (pre-1.1.0). Resolves the user id using
     * default {@link EiaeProperties} (attribute name {@code "userId"}).
     */
    public RequiresQuotaAspect(EntitlementService entitlementService) {
        this(entitlementService, new EiaeProperties());
    }

    /**
     * Intercepts methods annotated with {@link RequiresQuota}.
     *
     * @param pjp the proceeding join point
     * @param req the annotation instance
     * @return the return value of the intercepted method
     * @throws QuotaExceededException if the quota is exhausted
     * @throws Throwable              if the underlying method throws
     */
    @Around("@annotation(req)")
    public Object intercept(ProceedingJoinPoint pjp, RequiresQuota req) throws Throwable {
        String userId = resolveUserId();
        String feature = req.value();
        int amount = req.amount();
        String idempotencyKey = resolveIdempotencyKey(req.idempotencyKeyHeader());

        log.debug("[EIAE SDK] Consuming quota: userId={} feature={} amount={}", userId, feature, amount);

        QuotaResult result = entitlementService.consumeQuota(userId, feature, amount, idempotencyKey);

        if (!result.isAllowed()) {
            log.warn("[EIAE SDK] Quota DENIED: userId={} feature={}", userId, feature);
            throw new QuotaExceededException(feature, userId, result.periodResetsAt());
        }

        log.debug("[EIAE SDK] Quota ALLOWED: userId={} feature={} newConsumed={}/{}",
                userId, feature, result.newConsumed(), result.max());
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            if (isProviderFailure(ex)) {
                log.warn("[EIAE SDK] Provider-side failure after quota consume; refunding: userId={} feature={} idem={} cause={}",
                        userId, feature, idempotencyKey, ex.getClass().getSimpleName());
                try {
                    entitlementService.refundQuota(userId, feature, amount, idempotencyKey);
                } catch (Exception refundEx) {
                    log.error("[EIAE SDK] Best-effort quota refund failed for userId={} feature={} idem={}: {}",
                            userId, feature, idempotencyKey, refundEx.getMessage());
                }
            }
            throw ex;
        }
    }

    /**
     * Determines whether {@code ex} represents a provider-side failure eligible for
     * quota compensation (explicit {@link QuotaRefundable}, {@code ResponseStatusException}
     * 5xx, {@code @ResponseStatus} 5xx, or known upstream/provider failure classes).
     * Client validation errors (4xx, {@link IllegalArgumentException}) return {@code false}.
     */
    protected boolean isProviderFailure(Throwable ex) {
        java.util.Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Throwable cursor = ex;
        int depth = 0;
        while (cursor != null && depth < 16 && visited.add(cursor)) {
            if (cursor instanceof QuotaRefundable) {
                return true;
            }
            if (cursor instanceof org.springframework.web.server.ResponseStatusException rse
                    && rse.getStatusCode().is5xxServerError()) {
                return true;
            }
            org.springframework.web.bind.annotation.ResponseStatus rs =
                    cursor.getClass().getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class);
            if (rs != null && (rs.value().is5xxServerError() || rs.code().is5xxServerError())) {
                return true;
            }
            String className = cursor.getClass().getName();
            if (className.contains("AiProvidersUnavailable")
                    || className.contains("HttpServerErrorException")
                    || className.contains("ServiceUnavailable")
                    || className.contains("InternalServerError")
                    || className.contains("BadGateway")
                    || className.contains("GatewayTimeout")
                    || className.contains("ResourceAccessException")
                    || className.contains("SocketTimeoutException")) {
                return true;
            }
            cursor = cursor.getCause();
            depth++;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    protected String resolveUserId() {
        return EiaeUserIdResolver.resolveUserId(properties);
    }

    protected String resolveIdempotencyKey(String headerName) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            String headerValue = request.getHeader(headerName);
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue;
            }
        } catch (IllegalStateException e) {
            // No request context (e.g., called from a scheduled task)
            log.debug("[EIAE SDK] No servlet request context, generating idempotency key");
        }
        return UUID.randomUUID().toString();
    }
}
