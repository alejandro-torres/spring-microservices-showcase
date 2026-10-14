package com.showcase.auth.infrastructure.rest.dto.request;

import com.showcase.auth.infrastructure.rest.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta pública: sin roles (se asigna USER). El nombre y los apellidos son para el perfil de cliente que crea
 * customer-service al recibir el evento {@code user.registered}.
 */
public record RegisterUserRequestDTO(
        @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @StrongPassword String password,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 120) String lastName) {

    /** Evita que la contraseña aparezca en los logs si se imprime el DTO. */
    @Override
    public String toString() {
        return "RegisterUserRequestDTO[username=" + username + ", email=" + email + ", firstName=" + firstName
                + ", lastName=" + lastName + "]";
    }
}
