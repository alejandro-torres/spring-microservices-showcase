package com.showcase.auth.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdatePermissionRequestDTO(
        @Size(max = 255) String description,
        @NotNull @PositiveOrZero Long version) {
}
