package com.showcase.common.infrastructure.messaging.outbox;

import com.showcase.common.infrastructure.messaging.config.MessagingProperties;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.EventTypes;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores an event in the {@code outbox_event} table within the transaction of the business change: either both
 * are saved or neither. It sends nothing to RabbitMQ; {@link OutboxPublisher} does that afterwards.
 * <p>
 * {@code Propagation.MANDATORY}: calling it without an open transaction is a programming error (the event would be
 * saved even if the business change failed), so Spring throws an exception instead of opening a new one.
 * It uses JDBC rather than JPA so the same class serves all services without registering entities; it shares the
 * connection of the current JPA transaction.
 */
@RequiredArgsConstructor
public class OutboxEventWriter {

    private static final String INSERT = """
            INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, exchange, routing_key, payload,
                                      trace_parent, status, attempts, created_at)
            VALUES (:id, :aggregateType, :aggregateId, :eventType, :exchange, :routingKey, CAST(:payload AS jsonb),
                    :traceParent, 'PENDING', 0, :createdAt)
            """;

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;
    private final MessagingProperties properties;
    private final Clock clock;
    private final OutboxTracing tracing;

    /**
     * Wraps the message in the common envelope and stores it pending send. The routing key is the event type.
     *
     * @return the assigned {@code eventId}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String aggregateType, Object aggregateId, String eventType, Object message,
                       Instant occurredAt) {
        Objects.requireNonNull(properties.exchange(), "showcase.messaging.exchange");
        UUID eventId = UUID.randomUUID();
        var envelope = new EventEnvelopeDTO<>(eventId, eventType, EventTypes.CURRENT_VERSION, occurredAt,
                properties.source(), message);
        jdbcClient.sql(INSERT)
                .param("id", eventId)
                .param("aggregateType", aggregateType)
                .param("aggregateId", String.valueOf(aggregateId))
                .param("eventType", eventType)
                .param("exchange", properties.exchange())
                .param("routingKey", eventType)
                .param("payload", jsonMapper.writeValueAsString(envelope))
                .param("traceParent", tracing.currentTraceParent())
                .param("createdAt", Timestamp.from(Instant.now(clock)))
                .update();
        return eventId;
    }
}
