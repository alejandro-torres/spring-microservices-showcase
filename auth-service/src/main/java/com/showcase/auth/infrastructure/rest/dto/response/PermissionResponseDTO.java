package com.showcase.auth.infrastructure.rest.dto.response;

import java.time.Instant;

public record PermissionResponseDTO(
        Long id,
        String name,
        String description,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
