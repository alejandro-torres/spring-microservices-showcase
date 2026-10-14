package com.showcase.auth.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code name} con el formato recurso:accion, p. ej. {@code product:write}. */
public record CreatePermissionRequestDTO(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 255) String description) {
}
