package com.showcase.common.domain.messaging;

import java.util.UUID;

/**
 * Registro de los eventos ya procesados por un consumidor (puerto de salida). RabbitMQ entrega "al menos una
 * vez", así que el mismo mensaje puede llegar dos veces; los servicios lo registran en la MISMA transacción que
 * su cambio de negocio y lo ignoran si ya estaba.
 */
public interface ProcessedEventRegistry {

    /**
     * @return {@code true} si el evento es nuevo y queda registrado; {@code false} si ya se había procesado
     */
    boolean registerIfAbsent(UUID eventId, String eventType);
}
