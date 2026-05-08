package com.teclavya.eiae.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RequiresEntitlementAspect}.
 *
 * Uses AspectJ proxy factory directly to avoid needing a full Spring context.
 */
@DisplayName("RequiresEntitlementAspect")
class RequiresEntitlementAspectTest {

    private EntitlementService entitlementService;
    private SampleService proxy;

    @BeforeEach
    void setUp() {
        entitlementService = mock(EntitlementService.class);

        // Create a subclass of RequiresEntitlementAspect that returns a fixed userId
        RequiresEntitlementAspect aspect = new RequiresEntitlementAspect(entitlementService) {
            @Override
            protected String resolveUserId() {
                return "user-test";
            }
        };

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    @Test
    @DisplayName("Method proceeds when user has the required entitlement")
    void proceed_whenEntitlementPresent() {
        when(entitlementService.has("user-test", "mock_interviews")).thenReturn(true);

        String result = proxy.doSomething();

        assertThat(result).isEqualTo("ok");
        verify(entitlementService).has("user-test", "mock_interviews");
    }

    @Test
    @DisplayName("Throws EntitlementMissingException when user lacks the entitlement")
    void throwsWhenEntitlementMissing() {
        when(entitlementService.has("user-test", "mock_interviews")).thenReturn(false);

        assertThatThrownBy(() -> proxy.doSomething())
                .isInstanceOf(EntitlementMissingException.class)
                .satisfies(ex -> {
                    EntitlementMissingException e = (EntitlementMissingException) ex;
                    assertThat(e.getFeature()).isEqualTo("mock_interviews");
                    assertThat(e.getUserId()).isEqualTo("user-test");
                });
    }

    @Test
    @DisplayName("Method without annotation is NOT intercepted")
    void notAnnotated_proceedsWithoutCheck() {
        // unprotected() has no @RequiresEntitlement — should call through
        String result = proxy.unprotected();

        assertThat(result).isEqualTo("unprotected");
        verifyNoInteractions(entitlementService);
    }

    @Test
    @DisplayName("EntitlementMissingException message contains feature and userId")
    void exceptionMessageContainsDetails() {
        when(entitlementService.has("user-test", "mock_interviews")).thenReturn(false);

        assertThatThrownBy(() -> proxy.doSomething())
                .hasMessageContaining("mock_interviews")
                .hasMessageContaining("user-test");
    }

    // -------------------------------------------------------------------------
    // Sample service for proxy testing
    // -------------------------------------------------------------------------

    static class SampleService {
        @RequiresEntitlement("mock_interviews")
        public String doSomething() {
            return "ok";
        }

        public String unprotected() {
            return "unprotected";
        }
    }
}
