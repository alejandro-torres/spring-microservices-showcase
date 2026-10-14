package com.showcase.auth.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateRoleRequestDTO(
        @NotBlank @Size(min = 2, max = 30) String name,
        @Size(max = 255) String description,
        Set<@Positive Long> permissionIds) {
}
