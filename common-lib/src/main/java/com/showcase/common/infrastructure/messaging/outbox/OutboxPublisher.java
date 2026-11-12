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
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
 * Sends the pending outbox events to RabbitMQ, in creation order and in batches.
 * <p>
 * Each batch is read with {@code SELECT ... FOR UPDATE SKIP LOCKED}: if two replicas run the task at once,
 * each takes different rows. In addition, ShedLock makes only one replica launch it on each run: without it,
 * every replica would repeat the same query and the work would multiply by the number of replicas.
 * The locks are held while the batch is sent, which is bounded by {@code batch-size} and by the publisher
 * confirm timeout.
 * <p>
 * An event only becomes SENT when RabbitMQ confirms it has received it (publisher confirm) and has not returned it
 * for lack of a destination queue (publisher return). If RabbitMQ does not respond, the batch is cut short so as
 * not to reorder events and is retried on the next run: nothing is lost.
 */
@RequiredArgsConstructor
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
    private final MessagingProperties properties;
    private final Clock clock;
    private final OutboxTracing tracing;

    @Scheduled(fixedDelayString = "${showcase.messaging.outbox.fixed-delay:5s}",
            initialDelayString = "${showcase.messaging.outbox.initial-delay:5s}")
    @SchedulerLock(name = "outboxPublisher",
            lockAtMostFor = "${showcase.messaging.outbox.lock-at-most-for:PT30S}",
            lockAtLeastFor = "${showcase.messaging.outbox.lock-at-least-for:PT1S}")
    public void publishPending() {
        if (!settings().enabled()) {
            return;
        }
        Integer sent = transactionTemplate.execute(status -> publishBatch());
        if (sent != null && sent > 0) {
            log.debug("Outbox: {} events sent to RabbitMQ", sent);
        }
    }

    private MessagingProperties.Outbox settings() {
        return properties.outbox();
    }

    /** Sends a batch within the current transaction and returns how many events were confirmed. */
    int publishBatch() {
        List<PendingEvent> batch = jdbcClient.sql(SELECT_PENDING)
                .param("limit", settings().batchSize())
                .query(PendingEvent.class)
                .list();
        int sent = 0;
        for (PendingEvent event : batch) {
            // The send continues the trace of the request that stored the event, not that of this scheduled task.
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
                    log.warn("Outbox: RabbitMQ has not confirmed event {} ({}); it will be retried", event.id(),
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
                    .get(settings().confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
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
     * No destination queue may be transient (the consumer has not started yet and has not declared its queue),
     * so it is retried a few times before setting it aside.
     */
    private void markUnrouted(PendingEvent event, String detail) {
        int attempts = event.attempts() + 1;
        OutboxStatus status = attempts >= settings().maxUnroutedAttempts() ? OutboxStatus.UNROUTED
                : OutboxStatus.PENDING;
        jdbcClient.sql("UPDATE outbox_event SET status = :status, attempts = :attempts, last_error = :error "
                        + "WHERE id = :id")
                .param("status", status.name())
                .param("attempts", attempts)
                .param("error", truncate("unroutable: " + detail))
                .param("id", event.id())
                .update();
        log.warn("Outbox: event {} ({}) has not reached any queue; attempt {} of {}", event.id(),
                event.eventType(), attempts, settings().maxUnroutedAttempts());
    }

    /** If RabbitMQ is down, attempts are not counted: the event waits as long as needed. */
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
