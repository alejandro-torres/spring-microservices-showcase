package com.showcase.auth.infrastructure.rest.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Actualización de usuario. {@code version} es la que devolvió el GET: si otro la ha cambiado
 * entretanto, la API responde 409.
 */
public record UpdateUserRequestDTO(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull Boolean enabled,
        @NotNull @PositiveOrZero Long version) {
}
