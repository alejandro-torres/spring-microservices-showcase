package com.showcase.common.domain.messaging;

import java.util.UUID;

/**
 * Registry of the events already processed by a consumer (outbound port). RabbitMQ delivers "at least once",
 * so the same message can arrive twice; the services register it in the SAME transaction as their
 * business change and ignore it if already there.
 */
public interface ProcessedEventRegistry {

    /**
     * @return {@code true} if the event is new and is now registered; {@code false} if it was already processed
     */
    boolean registerIfAbsent(UUID eventId, String eventType);
}
