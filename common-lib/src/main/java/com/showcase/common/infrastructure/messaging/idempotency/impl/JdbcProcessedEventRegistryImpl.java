package com.showcase.common.infrastructure.messaging.idempotency.impl;

import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers the event in {@code processed_event} with {@code INSERT ... ON CONFLICT DO NOTHING}. If two replicas
 * receive the same message at once, the second INSERT waits for the first transaction to end and inserts
 * nothing: only one processes the event. If the business change fails, the rollback also takes the registration
 * and the retry processes it as new.
 */
@RequiredArgsConstructor
public class JdbcProcessedEventRegistryImpl implements ProcessedEventRegistry {

    private static final String INSERT = """
            INSERT INTO processed_event (event_id, event_type, processed_at)
            VALUES (:eventId, :eventType, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcClient jdbcClient;
    private final Clock clock;

    /** {@code MANDATORY}: outside the use case's transaction it would protect from nothing. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registerIfAbsent(UUID eventId, String eventType) {
        return jdbcClient.sql(INSERT)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("processedAt", Timestamp.from(Instant.now(clock)))
                .update() == 1;
    }
}
