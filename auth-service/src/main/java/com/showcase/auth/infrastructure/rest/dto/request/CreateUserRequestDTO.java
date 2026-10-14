package com.showcase.auth.infrastructure.rest.dto.request;

import com.showcase.auth.infrastructure.rest.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Alta de usuario. La política de contraseñas completa (mayúsculas, símbolos, historial) llega con
 * la parte de seguridad; aquí solo se exige la longitud.
 */
public record CreateUserRequestDTO(
        @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @StrongPassword String password,
        Set<@Positive Long> roleIds) {

    /** Evita que la contraseña aparezca en los logs si se imprime el DTO. */
    @Override
    public String toString() {
        return "CreateUserRequestDTO[username=" + username + ", email=" + email + ", roleIds=" + roleIds + "]";
    }
}
