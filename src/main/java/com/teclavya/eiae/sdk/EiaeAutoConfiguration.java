package com.teclavya.eiae.sdk;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Spring Boot auto-configuration for the EIAE SDK.
 *
 * <p>Activates when:
 * <ul>
 *   <li>The application is a web application ({@link ConditionalOnWebApplication})
 *   <li>The consuming project imports this library via Maven/Gradle
 * </ul>
 *
 * <p>Beans created:
 * <ul>
 *   <li>{@link EntitlementServiceImpl} — always
 *   <li>{@link RequiresEntitlementAspect} — when spring-aop is on classpath
 *   <li>{@link RequiresQuotaAspect} — when spring-aop is on classpath
 *   <li>{@link PlanChangeSubscriber} + {@link RedisMessageListenerContainer}
 *       — when spring-data-redis is on classpath
 * </ul>
 */
@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties(EiaeProperties.class)
public class EiaeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntitlementService entitlementService(EiaeProperties properties) {
        return new EntitlementServiceImpl(properties);
    }

    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnMissingBean
    public RequiresEntitlementAspect requiresEntitlementAspect(
            EntitlementService entitlementService, EiaeProperties properties) {
        return new RequiresEntitlementAspect(entitlementService, properties);
    }

    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnMissingBean
    public RequiresQuotaAspect requiresQuotaAspect(
            EntitlementService entitlementService, EiaeProperties properties) {
        return new RequiresQuotaAspect(entitlementService, properties);
    }

    /**
     * Redis pub/sub container — only created when spring-data-redis is on the classpath.
     * Subscribes {@link PlanChangeSubscriber} to the {@code plan-changes} channel.
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    @ConditionalOnMissingBean(PlanChangeSubscriber.class)
    public PlanChangeSubscriber planChangeSubscriber(EntitlementService entitlementService) {
        return new PlanChangeSubscriber(entitlementService);
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    @ConditionalOnMissingBean(name = "eiaePlanChangeListenerContainer")
    public RedisMessageListenerContainer eiaePlanChangeListenerContainer(
            RedisConnectionFactory connectionFactory,
            PlanChangeSubscriber planChangeSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(planChangeSubscriber, new ChannelTopic(PlanChangeSubscriber.CHANNEL));
        return container;
    }
}
