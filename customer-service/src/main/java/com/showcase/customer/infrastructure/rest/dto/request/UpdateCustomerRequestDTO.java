package com.showcase.customer.infrastructure.rest.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Las direcciones tienen sus propios endpoints. */
public record UpdateCustomerRequestDTO(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @NotNull @PositiveOrZero Long version) {
}
