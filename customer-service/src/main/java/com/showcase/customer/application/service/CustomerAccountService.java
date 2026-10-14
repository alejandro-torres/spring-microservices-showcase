package com.showcase.customer.application.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Reacciones a los eventos de usuarios de auth-service (puerto de entrada de los listeners). {@code eventId} hace
 * idempotente cada operación: un mensaje repetido no tiene efecto.
 */
public interface CustomerAccountService {

    /** Crea el perfil de cliente de un usuario recién registrado (sin direcciones). */
    void createForUser(UUID eventId, Long userId, String email, String firstName, String lastName);

    /** Marca como inactivo el cliente de un usuario desactivado o borrado. */
    void deactivateForUser(UUID eventId, Long userId);

    /**
     * Registra que la cuenta del usuario se ha bloqueado. No cambia el cliente: el bloqueo es temporal y se
     * levanta solo, pero queda constancia en el log del servicio.
     */
    void recordAccountLocked(UUID eventId, Long userId, Instant lockedUntil);
}
