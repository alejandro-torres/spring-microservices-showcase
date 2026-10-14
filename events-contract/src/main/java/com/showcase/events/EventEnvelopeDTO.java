package com.showcase.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre común de todos los mensajes de RabbitMQ. {@code eventId} sirve para el consumo idempotente y
 * {@code version} permite evolucionar el contrato de un evento sin romper a los consumidores.
 *
 * @param <T> el {@code *MessageDTO} del evento
 */
public record EventEnvelopeDTO<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String source,
        T payload) {
}
