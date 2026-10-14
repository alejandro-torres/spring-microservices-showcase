package com.showcase.auth.domain.event;

import java.time.Instant;

/**
 * Alta pública de un usuario. Lleva el nombre y los apellidos que se piden al registrarse: auth-service no los
 * guarda, pero customer-service los necesita para crear el perfil de cliente.
 */
public record UserRegisteredEvent(Long userId, String username, String email, String firstName, String lastName,
                                  Instant occurredAt) {
}
