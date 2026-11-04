package com.showcase.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope of all RabbitMQ messages. {@code eventId} serves idempotent consumption and
 * {@code version} allows evolving an event's contract without breaking consumers.
 *
 * @param <T> the event's {@code *MessageDTO}
 */
public record EventEnvelopeDTO<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String source,
        T payload) {
}
