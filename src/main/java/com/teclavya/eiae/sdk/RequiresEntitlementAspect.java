package com.teclavya.eiae.sdk;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AOP aspect that enforces {@link RequiresEntitlement} on annotated methods.
 *
 * <p>Resolves the authenticated user ID from Spring Security's
 * {@code SecurityContextHolder} and delegates to {@link EntitlementService#has}.
 *
 * <p>Bean is created by {@link EiaeAutoConfiguration} only when spring-aop is
 * on the classpath.
 */
@Aspect
public class RequiresEntitlementAspect {

    private static final Logger log = LoggerFactory.getLogger(RequiresEntitlementAspect.class);

    private final EntitlementService entitlementService;
    private final EiaeProperties properties;

    public RequiresEntitlementAspect(EntitlementService entitlementService, EiaeProperties properties) {
        this.entitlementService = entitlementService;
        this.properties = properties;
    }

    /**
     * Backward-compatible constructor (pre-1.1.0). Resolves the user id using
     * default {@link EiaeProperties} (attribute name {@code "userId"}).
     */
    public RequiresEntitlementAspect(EntitlementService entitlementService) {
        this(entitlementService, new EiaeProperties());
    }

    /**
     * Intercepts methods annotated with {@link RequiresEntitlement}.
     *
     * @param pjp the proceeding join point
     * @param req the annotation instance
     * @return the return value of the intercepted method
     * @throws EntitlementMissingException if the user lacks the required feature
     * @throws Throwable                   if the underlying method throws
     */
    @Around("@annotation(req)")
    public Object intercept(ProceedingJoinPoint pjp, RequiresEntitlement req) throws Throwable {
        String userId = resolveUserId();
        String feature = req.value();

        if (!entitlementService.has(userId, feature)) {
            log.warn("[EIAE SDK] Entitlement check FAILED: userId={} feature={}", userId, feature);
            throw new EntitlementMissingException(feature, userId);
        }

        log.debug("[EIAE SDK] Entitlement check PASSED: userId={} feature={}", userId, feature);
        return pjp.proceed();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the current user's identifier from Spring Security context.
     * Falls back to {@code "anonymous"} if no authentication is present.
     */
    protected String resolveUserId() {
        return EiaeUserIdResolver.resolveUserId(properties);
    }
}
