package com.showcase.auth.infrastructure.rest.dto.request;

import com.showcase.auth.infrastructure.rest.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public sign-up: no roles (USER is assigned). The first and last names are for the customer profile that
 * customer-service creates on receiving the {@code user.registered} event.
 */
public record RegisterUserRequestDTO(
        @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @StrongPassword String password,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 120) String lastName) {

    /** Keeps the password out of the logs if the DTO is printed. */
    @Override
    public String toString() {
        return "RegisterUserRequestDTO[username=" + username + ", email=" + email + ", firstName=" + firstName
                + ", lastName=" + lastName + "]";
    }
}
