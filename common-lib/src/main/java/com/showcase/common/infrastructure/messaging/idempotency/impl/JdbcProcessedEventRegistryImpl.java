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
 * Registra el evento en {@code processed_event} con {@code INSERT ... ON CONFLICT DO NOTHING}. Si dos réplicas
 * reciben el mismo mensaje a la vez, el segundo INSERT espera a que la primera transacción termine y no inserta
 * nada: solo una procesa el evento. Si el cambio de negocio falla, el rollback se lleva también el registro y el
 * reintento lo procesa como nuevo.
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

    /** {@code MANDATORY}: fuera de la transacción del caso de uso no protegería de nada. */
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
