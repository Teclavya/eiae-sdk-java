package com.teclavya.eiae.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Redis pub/sub subscriber that listens on the {@code plan-changes} channel
 * and invalidates the Caffeine cache for affected users.
 *
 * <p>Expected message format: comma-separated user IDs, e.g.:
 * {@code "user-123,user-456"}
 *
 * <p>This bean is only created when {@code spring-data-redis} is on the
 * classpath — see {@link EiaeAutoConfiguration}.
 */
public class PlanChangeSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(PlanChangeSubscriber.class);

    public static final String CHANNEL = "plan-changes";

    private final EntitlementService entitlementService;

    public PlanChangeSubscriber(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    /**
     * Called by Spring Data Redis when a message arrives on the {@code plan-changes} channel.
     *
     * @param message the Redis message (body = UTF-8 comma-separated user IDs)
     * @param pattern the channel pattern (unused)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
        if (body.isEmpty()) {
            return;
        }

        List<String> userIds = Arrays.stream(body.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        log.info("[EIAE SDK] plan-changes received: invalidating {} user(s): {}", userIds.size(), userIds);

        for (String userId : userIds) {
            try {
                entitlementService.invalidateCache(userId);
            } catch (Exception ex) {
                log.error("[EIAE SDK] Failed to invalidate cache for userId={}: {}", userId, ex.getMessage());
            }
        }
    }
}
