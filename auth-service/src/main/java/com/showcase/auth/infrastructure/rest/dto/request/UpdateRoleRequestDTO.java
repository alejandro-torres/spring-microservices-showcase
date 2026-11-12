package com.showcase.auth.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Replaces the role's description and full set of permissions. */
public record UpdateRoleRequestDTO(
        @Size(max = 255) String description,
        @NotNull Set<@Positive Long> permissionIds,
        @NotNull @PositiveOrZero Long version) {
}
