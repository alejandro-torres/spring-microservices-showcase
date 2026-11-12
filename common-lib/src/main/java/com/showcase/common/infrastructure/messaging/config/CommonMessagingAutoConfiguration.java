package com.showcase.common.infrastructure.messaging.config;

import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.common.infrastructure.messaging.dlq.DeadLetterController;
import com.showcase.common.infrastructure.messaging.dlq.DeadLetterQueueAdmin;
import com.showcase.common.infrastructure.messaging.dlq.impl.RabbitDeadLetterQueueAdminImpl;
import com.showcase.common.infrastructure.messaging.error.LoggingRabbitListenerErrorHandler;
import com.showcase.common.infrastructure.messaging.error.MessagingExceptionClassifier;
import com.showcase.common.infrastructure.messaging.idempotency.ProcessedEventCleanup;
import com.showcase.common.infrastructure.messaging.idempotency.impl.JdbcProcessedEventRegistryImpl;
import com.showcase.common.infrastructure.messaging.outbox.OutboxEventWriter;
import com.showcase.common.infrastructure.messaging.outbox.OutboxMetrics;
import com.showcase.common.infrastructure.messaging.outbox.OutboxPublisher;
import com.showcase.common.infrastructure.messaging.outbox.OutboxTracing;
import com.showcase.common.infrastructure.messaging.outbox.impl.MicrometerOutboxTracingImpl;
import com.showcase.common.infrastructure.messaging.outbox.impl.NoopOutboxTracingImpl;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Common messaging for services with RabbitMQ: enabled by defining {@code showcase.messaging.source}.
 * <ul>
 *   <li>Topology (exchanges, queues, DLX and DLQ) from {@link MessagingProperties}.</li>
 *   <li>JSON messages with the service's Jackson 3 ({@code JacksonJsonMessageConverter}, the successor of
 *       {@code Jackson2JsonMessageConverter}).</li>
 *   <li>Listener retries: the policy ({@code spring.rabbitmq.listener.simple.retry.*}) is configured by
 *       Spring Boot; this adds which exceptions are not retried and that, once attempts run out, the message is
 *       rejected without requeueing so RabbitMQ sends it to the DLQ.</li>
 *   <li>Outbox (which keeps the request's trace up to the consumer), registry of processed events and
 *       DLQ administration.</li>
 * </ul>
 */
@AutoConfiguration(
        afterName = {"org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration"},
        beforeName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "showcase.messaging", name = "source")
@EnableConfigurationProperties(MessagingProperties.class)
public class CommonMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter jacksonJsonMessageConverter(ObjectProvider<JsonMapper> jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    @Bean
    public Declarables messagingDeclarables(MessagingProperties properties) {
        return MessagingTopology.declarables(properties);
    }

    /** After the last attempt, rejected without requeueing: RabbitMQ sends it to the queue's DLX. */
    @Bean
    @ConditionalOnMissingBean
    public MessageRecoverer messageRecoverer() {
        return new RejectAndDontRequeueRecoverer();
    }

    @Bean
    public RabbitListenerRetrySettingsCustomizer nonRetryableExceptionsCustomizer() {
        return settings -> settings.setExceptionPredicate(MessagingExceptionClassifier::isRetryable);
    }

    @Bean(LoggingRabbitListenerErrorHandler.BEAN_NAME)
    @ConditionalOnMissingBean(name = LoggingRabbitListenerErrorHandler.BEAN_NAME)
    public LoggingRabbitListenerErrorHandler messagingErrorHandler() {
        return new LoggingRabbitListenerErrorHandler();
    }

    /** Outbox and idempotency: they store in the service's database, in its same transaction. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcClient.class)
    static class PersistenceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ProcessedEventRegistry processedEventRegistry(JdbcClient jdbcClient, ObjectProvider<Clock> clock) {
            return new JdbcProcessedEventRegistryImpl(jdbcClient, clock.getIfAvailable(Clock::systemUTC));
        }

        /** Only in the services that consume events (those that define how long processed ones are kept). */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "showcase.messaging", name = "processed-event-retention")
        public ProcessedEventCleanup processedEventCleanup(
                JdbcClient jdbcClient, ObjectProvider<Clock> clock,
                @Value("${showcase.messaging.processed-event-retention}") Duration retention) {
            return new ProcessedEventCleanup(jdbcClient, retention, clock.getIfAvailable(Clock::systemUTC));
        }
    }

    /** Only in the services that publish events (those with {@code showcase.messaging.exchange}). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcClient.class)
    @ConditionalOnProperty(prefix = "showcase.messaging", name = "exchange")
    @EnableScheduling
    static class OutboxConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OutboxEventWriter outboxEventWriter(JdbcClient jdbcClient, ObjectProvider<JsonMapper> jsonMapper,
                                                   MessagingProperties properties, ObjectProvider<Clock> clock,
                                                   ObjectProvider<OutboxTracing> tracing) {
            return new OutboxEventWriter(jdbcClient, jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()),
                    properties, clock.getIfAvailable(Clock::systemUTC),
                    tracing.getIfAvailable(NoopOutboxTracingImpl::new));
        }

        @Bean
        @ConditionalOnMissingBean
        public OutboxPublisher outboxPublisher(JdbcClient jdbcClient, RabbitTemplate rabbitTemplate,
                                               PlatformTransactionManager transactionManager,
                                               MessagingProperties properties, ObjectProvider<Clock> clock,
                                               ObjectProvider<OutboxTracing> tracing) {
            return new OutboxPublisher(jdbcClient, rabbitTemplate, new TransactionTemplate(transactionManager),
                    properties, clock.getIfAvailable(Clock::systemUTC),
                    tracing.getIfAvailable(NoopOutboxTracingImpl::new));
        }
    }

    /** Pending and unrouted outbox events as a metric, in the services that publish events. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"io.micrometer.core.instrument.binder.MeterBinder",
            "org.springframework.jdbc.core.simple.JdbcClient"})
    @ConditionalOnProperty(prefix = "showcase.messaging", name = "exchange")
    static class OutboxMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OutboxMetrics outboxMetrics(JdbcClient jdbcClient) {
            return new OutboxMetrics(jdbcClient);
        }
    }

    /** With Micrometer Tracing, the request's trace continues through the outbox to the consumer. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class OutboxTracingConfiguration {

        /** Without a Tracer or Propagator (e.g. with tracing disabled in tests) the outbox does not link the traces. */
        @Bean
        @ConditionalOnMissingBean
        public OutboxTracing outboxTracing(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
            Tracer availableTracer = tracer.getIfAvailable();
            Propagator availablePropagator = propagator.getIfAvailable();
            return availableTracer == null || availablePropagator == null ? new NoopOutboxTracingImpl()
                    : new MicrometerOutboxTracingImpl(availableTracer, availablePropagator);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class DeadLetterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public DeadLetterQueueAdmin deadLetterQueueAdmin(RabbitTemplate rabbitTemplate,
                                                         MessagingProperties properties) {
            return new RabbitDeadLetterQueueAdminImpl(rabbitTemplate, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        public DeadLetterController deadLetterController(DeadLetterQueueAdmin deadLetterQueueAdmin) {
            return new DeadLetterController(deadLetterQueueAdmin);
        }
    }
}
