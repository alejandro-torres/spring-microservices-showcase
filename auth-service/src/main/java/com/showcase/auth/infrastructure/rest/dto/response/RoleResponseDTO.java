package com.showcase.auth.infrastructure.rest.dto.response;

import java.time.Instant;
import java.util.Set;

public record RoleResponseDTO(
        Long id,
        String name,
        String description,
        Set<String> permissions,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
