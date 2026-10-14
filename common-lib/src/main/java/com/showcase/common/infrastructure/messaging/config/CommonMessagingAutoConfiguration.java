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
 * Mensajería común de los servicios con RabbitMQ: se activa al definir {@code showcase.messaging.source}.
 * <ul>
 *   <li>Topología (exchanges, colas, DLX y DLQ) a partir de {@link MessagingProperties}.</li>
 *   <li>Mensajes en JSON con el Jackson 3 del servicio ({@code JacksonJsonMessageConverter}, el sucesor de
 *       {@code Jackson2JsonMessageConverter}).</li>
 *   <li>Reintentos de los listeners: la política ({@code spring.rabbitmq.listener.simple.retry.*}) la configura
 *       Spring Boot; aquí se añade qué excepciones no se reintentan y que, al agotar los intentos, el mensaje se
 *       rechaza sin reencolar para que RabbitMQ lo mande a la DLQ.</li>
 *   <li>Outbox (que conserva la traza de la petición hasta el consumidor), registro de eventos procesados y
 *       administración de las DLQ.</li>
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

    /** Tras el último intento, rechazo sin reencolar: RabbitMQ lo manda al DLX de la cola. */
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

    /** Outbox e idempotencia: guardan en la base de datos del servicio, en su misma transacción. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcClient.class)
    static class PersistenceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public ProcessedEventRegistry processedEventRegistry(JdbcClient jdbcClient, ObjectProvider<Clock> clock) {
            return new JdbcProcessedEventRegistryImpl(jdbcClient, clock.getIfAvailable(Clock::systemUTC));
        }

        /** Solo en los servicios que consumen eventos (los que definen cuánto se guardan los procesados). */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "showcase.messaging", name = "processed-event-retention")
        public ProcessedEventCleanup processedEventCleanup(
                JdbcClient jdbcClient, ObjectProvider<Clock> clock,
                @Value("${showcase.messaging.processed-event-retention}") Duration retention) {
            return new ProcessedEventCleanup(jdbcClient, retention, clock.getIfAvailable(Clock::systemUTC));
        }
    }

    /** Solo en los servicios que publican eventos (los que tienen {@code showcase.messaging.exchange}). */
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

    /** Con Micrometer Tracing, la traza de la petición sigue por el outbox hasta el consumidor. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class OutboxTracingConfiguration {

        /** Sin Tracer o Propagator (p. ej. con el tracing desactivado en los tests) el outbox no une las trazas. */
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
