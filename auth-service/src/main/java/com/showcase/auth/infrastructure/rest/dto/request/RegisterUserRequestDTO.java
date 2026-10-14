package com.showcase.auth.infrastructure.rest.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta pública: sin roles (se asigna USER). */
public record RegisterUserRequestDTO(
        @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    /** Evita que la contraseña aparezca en los logs si se imprime el DTO. */
    @Override
    public String toString() {
        return "RegisterUserRequestDTO[username=" + username + ", email=" + email + "]";
    }
}
