package com.showcase.auth.infrastructure.rest.dto.request;

import com.showcase.auth.infrastructure.rest.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequestDTO(
        @NotBlank String currentPassword,
        @NotBlank @StrongPassword String newPassword) {

    /** Keeps the passwords out of the logs if the DTO is printed. */
    @Override
    public String toString() {
        return "ChangePasswordRequestDTO[***]";
    }
}
