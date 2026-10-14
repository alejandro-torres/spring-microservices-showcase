package com.showcase.common.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.common.infrastructure.messaging.config.MessagingProperties;
import com.showcase.common.infrastructure.messaging.dlq.DeadLetterQueueAdmin;
import com.showcase.common.infrastructure.messaging.dlq.impl.RabbitDeadLetterQueueAdminImpl;
import com.showcase.common.infrastructure.messaging.idempotency.ProcessedEventCleanup;
import com.showcase.common.infrastructure.messaging.outbox.OutboxEventWriter;
import com.showcase.common.infrastructure.messaging.outbox.OutboxMetrics;
import com.showcase.common.infrastructure.messaging.outbox.OutboxPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox, idempotencia y DLQ contra PostgreSQL y RabbitMQ reales. El publicador programado se retrasa una hora
 * para llamarlo a mano y que los tests sean deterministas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:messaging-schema.sql",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.publisher-returns=true",
        "spring.rabbitmq.template.mandatory=true",
        "showcase.messaging.source=test-service",
        "showcase.messaging.exchange=test.events",
        "showcase.messaging.consumers.thing-happened.queue=test-service.thing-happened",
        "showcase.messaging.consumers.thing-happened.exchange=test.events",
        "showcase.messaging.consumers.thing-happened.routing-key=thing.happened",
        "showcase.messaging.outbox.max-unrouted-attempts=2",
        "showcase.messaging.outbox.initial-delay=1h",
        "showcase.messaging.outbox.lock-at-least-for=PT0S",
        "showcase.messaging.processed-event-retention=7d"})
@Testcontainers
class MessagingInfrastructureIT {

    private static final String QUEUE = "test-service.thing-happened";
    private static final String DLQ = QUEUE + ".dlq";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3-management");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    record ThingHappened(Long thingId) {
    }

    @Autowired
    private OutboxEventWriter writer;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private ProcessedEventCleanup processedEventCleanup;

    @Autowired
    private OutboxMetrics outboxMetrics;

    @Autowired
    private ProcessedEventRegistry registry;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private MessagingProperties properties;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void cleanUp() {
        jdbcClient.sql("TRUNCATE outbox_event, processed_event").update();
        // Se libera el lock en lugar de borrar la fila: ShedLock recuerda las filas que ya ha creado y después
        // solo hace UPDATE; sin la fila, creería que otra réplica tiene el lock.
        jdbcClient.sql("UPDATE shedlock SET lock_until = timezone('utc', now()) - interval '1 second'").update();
        amqpAdmin.purgeQueue(QUEUE, false);
        amqpAdmin.purgeQueue(DLQ, false);
    }

    @Nested
    @DisplayName("outbox")
    class Outbox {

        @Test
        @DisplayName("no se puede guardar un evento sin una transacción abierta (MANDATORY)")
        void append_withoutTransaction_shouldFail() {
            assertThatThrownBy(() -> writer.append("Thing", 1L, "thing.happened", new ThingHappened(1L),
                    Instant.now())).isInstanceOf(IllegalTransactionStateException.class);
        }

        @Test
        @DisplayName("la métrica outbox.events cuenta los eventos pendientes y deja de contarlos al enviarlos")
        void outboxMetrics_shouldCountPendingEvents() {
            // given
            var registry = new SimpleMeterRegistry();
            outboxMetrics.bindTo(registry);
            transactionTemplate.executeWithoutResult(status -> writer.append("Thing", 5L, "thing.happened",
                    new ThingHappened(5L), Instant.now()));

            // when / then
            assertThat(registry.get("outbox.events").tag("status", "pending").gauge().value()).isEqualTo(1.0);
            publisher.publishPending();
            assertThat(registry.get("outbox.events").tag("status", "pending").gauge().value()).isZero();
        }

        @Test
        @DisplayName("si la transacción hace rollback, el evento tampoco se guarda")
        void append_whenTransactionRollsBack_shouldNotKeepEvent() {
            // when
            transactionTemplate.executeWithoutResult(status -> {
                writer.append("Thing", 1L, "thing.happened", new ThingHappened(1L), Instant.now());
                status.setRollbackOnly();
            });

            // then
            assertThat(countByStatus("PENDING")).isZero();
        }

        @Test
        @DisplayName("publica el sobre en el exchange del servicio y marca el evento como SENT tras el confirm")
        void publishPending_withRoutableEvent_shouldSendAndMarkSent() {
            // given
            UUID eventId = transactionTemplate.execute(status -> writer.append("Thing", 7L, "thing.happened",
                    new ThingHappened(7L), Instant.parse("2026-10-14T10:00:00Z")));

            // when
            publisher.publishPending();

            // then
            Message received = rabbitTemplate.receive(QUEUE, 5_000);
            assertThat(received).isNotNull();
            assertThat(received.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
            assertThat(received.getMessageProperties().getType()).isEqualTo("thing.happened");
            JsonNode envelope = jsonMapper.readTree(new String(received.getBody(), StandardCharsets.UTF_8));
            assertThat(envelope.get("eventId").asString()).isEqualTo(eventId.toString());
            assertThat(envelope.get("source").asString()).isEqualTo("test-service");
            assertThat(envelope.get("occurredAt").asString()).isEqualTo("2026-10-14T10:00:00Z");
            assertThat(envelope.at("/payload/thingId").asLong()).isEqualTo(7L);
            assertThat(countByStatus("SENT")).isEqualTo(1);
        }

        @Test
        @DisplayName("un evento sin cola de destino se reintenta y, al llegar al máximo, queda UNROUTED")
        void publishPending_withUnroutableEvent_shouldEndUnrouted() {
            // given
            transactionTemplate.executeWithoutResult(status -> writer.append("Thing", 8L, "thing.ignored",
                    new ThingHappened(8L), Instant.now()));

            // when
            publisher.publishPending();

            // then
            assertThat(countByStatus("PENDING")).isEqualTo(1);

            // when
            publisher.publishPending();

            // then
            assertThat(countByStatus("UNROUTED")).isEqualTo(1);
            assertThat(jdbcClient.sql("SELECT last_error FROM outbox_event").query(String.class).single())
                    .startsWith("unroutable");
        }
    }

    @Nested
    @DisplayName("ShedLock con varias réplicas")
    class SchedulerLocks {

        @Test
        @DisplayName("el publicador del outbox coge el lock outboxPublisher en la tabla shedlock")
        void publishPending_shouldTakeSchedulerLock() {
            // given
            LocalDateTime before = jdbcClient.sql("SELECT timezone('utc', now())")
                    .query(LocalDateTime.class).single();

            // when
            publisher.publishPending();

            // then: la fila del lock se ha actualizado en esta ejecución
            LocalDateTime lockedAt = jdbcClient.sql("SELECT locked_at FROM shedlock WHERE name = 'outboxPublisher'")
                    .query(LocalDateTime.class).single();
            assertThat(lockedAt).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("si otra réplica tiene el lock, esta no publica nada en esa ejecución")
        void publishPending_whileOtherReplicaHoldsLock_shouldSkip() {
            // given: otra réplica tiene el lock durante un minuto
            jdbcClient.sql("""
                    INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
                    VALUES ('outboxPublisher', timezone('utc', now()) + interval '1 minute', timezone('utc', now()),
                            'otra-replica')""").update();
            transactionTemplate.executeWithoutResult(status -> writer.append("Thing", 9L, "thing.happened",
                    new ThingHappened(9L), Instant.now()));

            // when
            publisher.publishPending();

            // then
            assertThat(countByStatus("PENDING")).isEqualTo(1);
            assertThat(rabbitTemplate.receive(QUEUE, 500)).isNull();
        }

        @Test
        @DisplayName("la limpieza borra los eventos procesados más antiguos que la retención")
        void cleanup_shouldDeleteOnlyOldProcessedEvents() {
            // given
            UUID oldEvent = UUID.randomUUID();
            UUID recentEvent = UUID.randomUUID();
            jdbcClient.sql("""
                    INSERT INTO processed_event (event_id, event_type, processed_at) VALUES
                    (:old, 'thing.happened', now() - interval '8 days'),
                    (:recent, 'thing.happened', now() - interval '1 day')""")
                    .param("old", oldEvent).param("recent", recentEvent).update();

            // when
            processedEventCleanup.deleteOldEvents();

            // then
            assertThat(jdbcClient.sql("SELECT event_id FROM processed_event").query(UUID.class).list())
                    .containsExactly(recentEvent);
        }
    }

    @Nested
    @DisplayName("eventos procesados")
    class ProcessedEvents {

        @Test
        @DisplayName("registra un evento nuevo e ignora el duplicado")
        void registerIfAbsent_withDuplicate_shouldReturnFalse() {
            // given
            UUID eventId = UUID.randomUUID();

            // when
            Boolean first = transactionTemplate.execute(status -> registry.registerIfAbsent(eventId, "thing.happened"));
            Boolean second = transactionTemplate.execute(status -> registry.registerIfAbsent(eventId, "thing.happened"));

            // then
            assertThat(first).isTrue();
            assertThat(second).isFalse();
        }

        @Test
        @DisplayName("exige una transacción abierta (MANDATORY)")
        void registerIfAbsent_withoutTransaction_shouldFail() {
            assertThatThrownBy(() -> registry.registerIfAbsent(UUID.randomUUID(), "thing.happened"))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
    }

    @Nested
    @DisplayName("administración de la DLQ")
    class DeadLetters {

        private DeadLetterQueueAdmin admin;

        @BeforeEach
        void setUp() {
            admin = new RabbitDeadLetterQueueAdminImpl(rabbitTemplate, properties);
            Message dead = MessageBuilder.withBody("{\"thingId\":9}".getBytes(StandardCharsets.UTF_8))
                    .setMessageId("dead-1").setType("thing.happened").build();
            rabbitTemplate.send("", DLQ, dead);
        }

        @Test
        @DisplayName("consulta los mensajes sin sacarlos de la DLQ")
        void peek_shouldNotRemoveMessages() {
            // when
            var messages = admin.peek(DLQ, 10);

            // then
            assertThat(messages).singleElement().satisfies(message -> {
                assertThat(message.messageId()).isEqualTo("dead-1");
                assertThat(message.eventType()).isEqualTo("thing.happened");
                assertThat(message.body()).isEqualTo("{\"thingId\":9}");
            });
            assertThat(admin.queues()).singleElement().satisfies(queue -> {
                assertThat(queue.name()).isEqualTo(DLQ);
                assertThat(queue.sourceQueue()).isEqualTo(QUEUE);
                assertThat(queue.messageCount()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("reenvía los mensajes a su cola original")
        void replay_shouldMoveMessagesToSourceQueue() {
            // when
            int replayed = admin.replay(DLQ, 10);

            // then
            assertThat(replayed).isEqualTo(1);
            Message received = rabbitTemplate.receive(QUEUE, 5_000);
            assertThat(received).isNotNull();
            assertThat(received.getMessageProperties().getMessageId()).isEqualTo("dead-1");
            assertThat(rabbitTemplate.receive(DLQ)).isNull();
        }

        @Test
        @DisplayName("una DLQ que no es de este servicio no existe (404)")
        void peek_withUnknownQueue_shouldThrowNotFound() {
            assertThatThrownBy(() -> admin.peek("other.queue.dlq", 10))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    private long countByStatus(String status) {
        return jdbcClient.sql("SELECT count(*) FROM outbox_event WHERE status = :status")
                .param("status", status).query(Long.class).single();
    }
}
