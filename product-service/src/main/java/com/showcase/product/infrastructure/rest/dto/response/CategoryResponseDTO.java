package com.showcase.product.infrastructure.rest.dto.response;

import java.time.Instant;

public record CategoryResponseDTO(
        Long id,
        String name,
        String description,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
