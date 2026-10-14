package com.showcase.auth.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Sustituye la descripción y el conjunto completo de permisos del rol. */
public record UpdateRoleRequestDTO(
        @Size(max = 255) String description,
        @NotNull Set<@Positive Long> permissionIds,
        @NotNull @PositiveOrZero Long version) {
}
