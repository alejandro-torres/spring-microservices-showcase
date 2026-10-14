package com.showcase.product.infrastructure.rest.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponseDTO(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        Long categoryId,
        String categoryName,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
