package com.teclavya.eiae.sdk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EiaeUserIdResolver}: the platform-UUID-first resolution shared by
 * {@link RequiresQuotaAspect} and {@link RequiresEntitlementAspect}.
 */
@DisplayName("EiaeUserIdResolver")
class EiaeUserIdResolverTest {

    private static final String UUID_STR = "3f6b8e2e-6b9c-4b1e-9f0a-8f2a6c1d4e10";

    private EiaeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EiaeProperties();
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Reads the platform UUID from the configured request attribute FIRST")
    void resolvesFromRequestAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", UUID_STR);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Even with an authenticated (email) principal present, the attribute wins.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@teclavya.com", "n/a", List.of()));

        String resolved = EiaeUserIdResolver.resolveUserId(properties);

        assertThat(resolved).isEqualTo(UUID_STR);
    }

    @Test
    @DisplayName("Honors a custom configured attribute name")
    void resolvesFromCustomAttributeName() {
        properties.setUserIdRequestAttribute("platformUserId");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("platformUserId", UUID_STR);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String resolved = EiaeUserIdResolver.resolveUserId(properties);

        assertThat(resolved).isEqualTo(UUID_STR);
    }

    @Test
    @DisplayName("Attribute absent, principal name IS a UUID: falls back to the principal")
    void fallsBackToUuidPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID_STR, "n/a", List.of()));

        String resolved = EiaeUserIdResolver.resolveUserId(properties);

        assertThat(resolved).isEqualTo(UUID_STR);
    }

    @Test
    @DisplayName("Attribute absent, principal name is an EMAIL (not a UUID): returns anonymous")
    void nonUuidPrincipal_returnsAnonymous() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@teclavya.com", "n/a", List.of()));

        String resolved = EiaeUserIdResolver.resolveUserId(properties);

        assertThat(resolved).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("No request context and no authentication: returns anonymous")
    void noContextNoAuth_returnsAnonymous() {
        String resolved = EiaeUserIdResolver.resolveUserId(properties);

        assertThat(resolved).isEqualTo("anonymous");
    }
}
