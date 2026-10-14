package com.showcase.common.infrastructure.messaging.outbox;

import com.showcase.common.infrastructure.messaging.config.MessagingProperties;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envía a RabbitMQ los eventos pendientes del outbox, en orden de creación y por lotes.
 * <p>
 * Cada lote se lee con {@code SELECT ... FOR UPDATE SKIP LOCKED}: si dos réplicas ejecutan la tarea a la vez,
 * cada una coge filas distintas (en la fase de escalado se añade ShedLock para que además solo la ejecute una).
 * Los bloqueos se mantienen mientras se envía el lote, que está acotado por {@code batch-size} y por el timeout
 * del publisher confirm.
 * <p>
 * Un evento solo pasa a SENT cuando RabbitMQ confirma que lo ha recibido (publisher confirm) y no lo ha devuelto
 * por no tener cola de destino (publisher return). Si RabbitMQ no responde se corta el lote para no desordenar
 * los eventos y se reintenta en la siguiente ejecución: no se pierde nada.
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final String SELECT_PENDING = """
            SELECT id, exchange, routing_key, event_type, CAST(payload AS text) AS payload, trace_parent, attempts
            FROM outbox_event
            WHERE status = 'PENDING'
            ORDER BY created_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """;

    private final JdbcClient jdbcClient;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MessagingProperties.Outbox settings;
    private final Clock clock;
    private final OutboxTracing tracing;

    public OutboxPublisher(JdbcClient jdbcClient, RabbitTemplate rabbitTemplate,
                           TransactionTemplate transactionTemplate, MessagingProperties properties, Clock clock,
                           OutboxTracing tracing) {
        this.jdbcClient = jdbcClient;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.settings = properties.outbox();
        this.clock = clock;
        this.tracing = tracing;
    }

    @Scheduled(fixedDelayString = "${showcase.messaging.outbox.fixed-delay:5s}",
            initialDelayString = "${showcase.messaging.outbox.initial-delay:5s}")
    public void publishPending() {
        if (!settings.enabled()) {
            return;
        }
        Integer sent = transactionTemplate.execute(status -> publishBatch());
        if (sent != null && sent > 0) {
            log.debug("Outbox: {} eventos enviados a RabbitMQ", sent);
        }
    }

    /** Envía un lote dentro de la transacción en curso y devuelve cuántos eventos se han confirmado. */
    int publishBatch() {
        List<PendingEvent> batch = jdbcClient.sql(SELECT_PENDING)
                .param("limit", settings.batchSize())
                .query(PendingEvent.class)
                .list();
        int sent = 0;
        for (PendingEvent event : batch) {
            // El envío continúa la traza de la petición que guardó el evento, no la de esta tarea programada.
            SendResult result = tracing.callWithParent(event.traceParent(), "outbox " + event.eventType(),
                    () -> send(event));
            switch (result.outcome()) {
                case CONFIRMED -> {
                    markSent(event.id());
                    sent++;
                }
                case UNROUTED -> markUnrouted(event, result.detail());
                case FAILED -> {
                    recordFailure(event.id(), result.detail());
                    log.warn("Outbox: RabbitMQ no ha confirmado el evento {} ({}); se reintentará", event.id(),
                            result.detail());
                    return sent;
                }
            }
        }
        return sent;
    }

    private SendResult send(PendingEvent event) {
        Message message = MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.id().toString())
                .setType(event.eventType())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(event.id().toString());
        try {
            rabbitTemplate.send(event.exchange(), event.routingKey(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(settings.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) {
                return new SendResult(Outcome.UNROUTED, correlation.getReturned().getReplyText());
            }
            return confirm.ack() ? new SendResult(Outcome.CONFIRMED, null)
                    : new SendResult(Outcome.FAILED, "nack: " + confirm.reason());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SendResult(Outcome.FAILED, "interrupted");
        } catch (Exception e) {
            return new SendResult(Outcome.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void markSent(UUID id) {
        jdbcClient.sql("UPDATE outbox_event SET status = 'SENT', sent_at = :now, last_error = NULL WHERE id = :id")
                .param("now", Timestamp.from(Instant.now(clock)))
                .param("id", id)
                .update();
    }

    /**
     * Sin cola de destino puede ser algo pasajero (el consumidor aún no ha arrancado y no ha declarado su cola),
     * así que se reintenta unas cuantas veces antes de apartarlo.
     */
    private void markUnrouted(PendingEvent event, String detail) {
        int attempts = event.attempts() + 1;
        OutboxStatus status = attempts >= settings.maxUnroutedAttempts() ? OutboxStatus.UNROUTED
                : OutboxStatus.PENDING;
        jdbcClient.sql("UPDATE outbox_event SET status = :status, attempts = :attempts, last_error = :error "
                        + "WHERE id = :id")
                .param("status", status.name())
                .param("attempts", attempts)
                .param("error", truncate("unroutable: " + detail))
                .param("id", event.id())
                .update();
        log.warn("Outbox: el evento {} ({}) no ha llegado a ninguna cola; intento {} de {}", event.id(),
                event.eventType(), attempts, settings.maxUnroutedAttempts());
    }

    /** Si RabbitMQ está caído no se cuentan los intentos: el evento espera lo que haga falta. */
    private void recordFailure(UUID id, String detail) {
        jdbcClient.sql("UPDATE outbox_event SET last_error = :error WHERE id = :id")
                .param("error", truncate(detail))
                .param("id", id)
                .update();
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 500);
    }

    record PendingEvent(UUID id, String exchange, String routingKey, String eventType, String payload,
                        String traceParent, int attempts) {
    }

    private enum Outcome { CONFIRMED, UNROUTED, FAILED }

    private record SendResult(Outcome outcome, String detail) {
    }
}
