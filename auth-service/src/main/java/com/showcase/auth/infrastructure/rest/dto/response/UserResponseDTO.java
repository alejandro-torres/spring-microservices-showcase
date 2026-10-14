package com.showcase.auth.infrastructure.rest.dto.response;

import java.time.Instant;
import java.util.Set;

/** Nunca incluye la contraseña ni su hash. */
public record UserResponseDTO(
        Long id,
        String username,
        String email,
        boolean enabled,
        boolean active,
        Set<String> roles,
        Instant lockedUntil,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
