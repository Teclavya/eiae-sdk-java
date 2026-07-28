package com.teclavya.eiae.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.UUID;

/**
 * Shared resolution logic for {@link RequiresQuotaAspect} and
 * {@link RequiresEntitlementAspect}: resolves the platform UUID EIAE
 * recognizes for the current caller.
 *
 * <p>The Spring Security principal name ({@code Authentication#getName()}) is
 * frequently the user's email (e.g. the JWT subject in tps), not a UUID — EIAE
 * resolves users by {@code eiae_user.external_id} (UUID). Consuming services'
 * auth filters commonly stash the platform UUID as a request attribute
 * (default name {@code "userId"}, configurable via
 * {@link EiaeProperties#getUserIdRequestAttribute()}); that attribute is
 * preferred, with a fallback to the principal name only when it is itself a
 * parseable UUID.
 */
final class EiaeUserIdResolver {

    private static final Logger log = LoggerFactory.getLogger(EiaeUserIdResolver.class);

    private EiaeUserIdResolver() {
    }

    static String resolveUserId(EiaeProperties properties) {
        String attr = requestAttribute(properties.getUserIdRequestAttribute());
        if (attr != null && !attr.isBlank()) {
            return attr;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && isUuid(auth.getName())) {
            return auth.getName();
        }

        return "anonymous";
    }

    private static String requestAttribute(String name) {
        try {
            RequestAttributes attrs = RequestContextHolder.currentRequestAttributes();
            Object value = attrs.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
            return value != null ? value.toString() : null;
        } catch (IllegalStateException e) {
            // No request context (e.g., called from a scheduled task)
            log.debug("[EIAE SDK] No servlet request context while resolving user id attribute '{}'", name);
            return null;
        }
    }

    private static boolean isUuid(String candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            UUID.fromString(candidate);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
