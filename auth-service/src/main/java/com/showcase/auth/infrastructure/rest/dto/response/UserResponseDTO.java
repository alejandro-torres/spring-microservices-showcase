package com.showcase.auth.infrastructure.rest.dto.response;

import java.time.Instant;
import java.util.Set;

/** Never includes the password or its hash. */
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
