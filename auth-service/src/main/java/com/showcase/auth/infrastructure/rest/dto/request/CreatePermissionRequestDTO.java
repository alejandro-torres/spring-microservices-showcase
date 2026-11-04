package com.showcase.auth.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code name} in the resource:action format, e.g. {@code product:write}. */
public record CreatePermissionRequestDTO(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 255) String description) {
}
