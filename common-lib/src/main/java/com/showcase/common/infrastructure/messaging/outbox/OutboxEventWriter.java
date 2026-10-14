package com.showcase.common.infrastructure.messaging.outbox;

import com.showcase.common.infrastructure.messaging.config.MessagingProperties;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.EventTypes;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda un evento en la tabla {@code outbox_event} dentro de la transacción del cambio de negocio: o se guardan
 * los dos o ninguno. No envía nada a RabbitMQ; eso lo hace después {@link OutboxPublisher}.
 * <p>
 * {@code Propagation.MANDATORY}: llamarlo sin una transacción abierta es un error de programación (el evento se
 * guardaría aunque el cambio de negocio fallara), así que Spring lanza una excepción en lugar de abrir una nueva.
 * Usa JDBC y no JPA para que la misma clase sirva a todos los servicios sin registrar entidades; comparte la
 * conexión de la transacción de JPA en curso.
 */
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

    public OutboxEventWriter(JdbcClient jdbcClient, JsonMapper jsonMapper, MessagingProperties properties,
                             Clock clock, OutboxTracing tracing) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.clock = clock;
        this.tracing = tracing;
    }

    /**
     * Envuelve el mensaje en el sobre común y lo guarda pendiente de enviar. La routing key es el tipo de evento.
     *
     * @return el {@code eventId} asignado
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
