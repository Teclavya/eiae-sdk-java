package com.teclavya.eiae.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RequiresQuotaAspect}.
 */
@DisplayName("RequiresQuotaAspect")
class RequiresQuotaAspectTest {

    private EntitlementService entitlementService;
    private SampleService proxy;

    @BeforeEach
    void setUp() {
        entitlementService = mock(EntitlementService.class);

        RequiresQuotaAspect aspect = new RequiresQuotaAspect(entitlementService) {
            @Override
            protected String resolveUserId() {
                return "user-quota-test";
            }

            @Override
            protected String resolveIdempotencyKey(String headerName) {
                return "test-idempotency-key";
            }
        };

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    @Test
    @DisplayName("Method proceeds when quota consumption is ALLOWED")
    void proceed_whenQuotaAllowed() {
        QuotaResult allowed = new QuotaResult(QuotaResult.ALLOWED, 1, 10, Instant.now().plusSeconds(3600));
        when(entitlementService.consumeQuota(eq("user-quota-test"), eq("mock_interviews"), eq(1), anyString()))
                .thenReturn(allowed);

        String result = proxy.startInterview();

        assertThat(result).isEqualTo("interview-started");
        verify(entitlementService).consumeQuota(eq("user-quota-test"), eq("mock_interviews"), eq(1), anyString());
    }

    @Test
    @DisplayName("Throws QuotaExceededException when quota is DENIED")
    void throwsWhenQuotaDenied() {
        Instant resetsAt = Instant.now().plusSeconds(86400);
        QuotaResult denied = new QuotaResult(QuotaResult.DENIED, 10, 10, resetsAt);
        when(entitlementService.consumeQuota(eq("user-quota-test"), eq("mock_interviews"), eq(1), anyString()))
                .thenReturn(denied);

        assertThatThrownBy(() -> proxy.startInterview())
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(ex -> {
                    QuotaExceededException e = (QuotaExceededException) ex;
                    assertThat(e.getFeature()).isEqualTo("mock_interviews");
                    assertThat(e.getUserId()).isEqualTo("user-quota-test");
                    assertThat(e.getPeriodResetsAt()).isEqualTo(resetsAt);
                });
    }

    @Test
    @DisplayName("Custom amount annotation value is passed to consumeQuota")
    void customAmount_isPassedToConsumeQuota() {
        QuotaResult allowed = new QuotaResult(QuotaResult.ALLOWED, 5, 100, Instant.now().plusSeconds(3600));
        when(entitlementService.consumeQuota(eq("user-quota-test"), eq("ai_resume_credits"), eq(5), anyString()))
                .thenReturn(allowed);

        proxy.bulkAction();

        verify(entitlementService).consumeQuota(eq("user-quota-test"), eq("ai_resume_credits"), eq(5), anyString());
    }

    @Test
    @DisplayName("QuotaExceededException message contains feature, userId, and resetAt")
    void exceptionMessageContainsDetails() {
        Instant resetsAt = Instant.now().plusSeconds(3600);
        QuotaResult denied = new QuotaResult(QuotaResult.DENIED, 10, 10, resetsAt);
        when(entitlementService.consumeQuota(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(denied);

        assertThatThrownBy(() -> proxy.startInterview())
                .hasMessageContaining("mock_interviews")
                .hasMessageContaining("user-quota-test");
    }

    // -------------------------------------------------------------------------
    // Sample service for proxy testing
    // -------------------------------------------------------------------------

    static class SampleService {
        @RequiresQuota(value = "mock_interviews", amount = 1)
        public String startInterview() {
            return "interview-started";
        }

        @RequiresQuota(value = "ai_resume_credits", amount = 5)
        public String bulkAction() {
            return "bulk-done";
        }
    }
}
