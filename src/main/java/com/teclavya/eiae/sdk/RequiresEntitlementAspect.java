package com.teclavya.eiae.sdk;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

    public RequiresEntitlementAspect(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "anonymous";
        }
        return auth.getName();
    }
}
