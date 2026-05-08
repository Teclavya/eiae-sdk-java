package com.teclavya.eiae.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link PlanChangeSubscriber}.
 */
@DisplayName("PlanChangeSubscriber")
class PlanChangeSubscriberTest {

    private EntitlementService entitlementService;
    private PlanChangeSubscriber subscriber;

    @BeforeEach
    void setUp() {
        entitlementService = mock(EntitlementService.class);
        subscriber = new PlanChangeSubscriber(entitlementService);
    }

    private Message toMessage(String body) {
        return new DefaultMessage(
                "plan-changes".getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    @DisplayName("Single userId: invalidates cache for that user")
    void singleUserId_invalidatesCache() {
        subscriber.onMessage(toMessage("user-123"), null);

        verify(entitlementService, times(1)).invalidateCache("user-123");
    }

    @Test
    @DisplayName("Multiple userIds: invalidates cache for each")
    void multipleUserIds_invalidatesEach() {
        subscriber.onMessage(toMessage("user-1,user-2,user-3"), null);

        verify(entitlementService).invalidateCache("user-1");
        verify(entitlementService).invalidateCache("user-2");
        verify(entitlementService).invalidateCache("user-3");
    }

    @Test
    @DisplayName("Empty message body: no cache invalidation")
    void emptyMessage_noInvalidation() {
        subscriber.onMessage(toMessage(""), null);

        verifyNoInteractions(entitlementService);
    }

    @Test
    @DisplayName("Whitespace-only: no cache invalidation")
    void whitespaceMessage_noInvalidation() {
        subscriber.onMessage(toMessage("   "), null);

        verifyNoInteractions(entitlementService);
    }

    @Test
    @DisplayName("Comma-separated with spaces: trims and invalidates each")
    void commaWithSpaces_trimmedCorrectly() {
        subscriber.onMessage(toMessage("user-a , user-b , user-c"), null);

        verify(entitlementService).invalidateCache("user-a");
        verify(entitlementService).invalidateCache("user-b");
        verify(entitlementService).invalidateCache("user-c");
    }
}
