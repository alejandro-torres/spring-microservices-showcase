package com.showcase.customer.infrastructure.rest.dto.response;

import java.time.Instant;
import java.util.List;

public record CustomerResponseDTO(
        Long id,
        Long userId,
        String firstName,
        String lastName,
        String email,
        String phone,
        List<AddressResponseDTO> addresses,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
