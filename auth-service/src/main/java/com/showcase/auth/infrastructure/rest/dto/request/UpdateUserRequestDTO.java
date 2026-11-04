package com.showcase.auth.infrastructure.rest.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * User update. {@code version} is the one returned by the GET: if someone else has changed it
 * in the meantime, the API responds 409.
 */
public record UpdateUserRequestDTO(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull Boolean enabled,
        @NotNull @PositiveOrZero Long version) {
}
