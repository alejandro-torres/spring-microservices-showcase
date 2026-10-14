package com.showcase.auth.infrastructure.rest.dto.request;

import com.showcase.auth.infrastructure.rest.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequestDTO(
        @NotBlank String currentPassword,
        @NotBlank @StrongPassword String newPassword) {

    /** Evita que las contraseñas aparezcan en los logs si se imprime el DTO. */
    @Override
    public String toString() {
        return "ChangePasswordRequestDTO[***]";
    }
}
