package com.showcase.customer.infrastructure.rest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code userId}: usuario de auth-service al que pertenece el cliente (opcional). */
public record CreateCustomerRequestDTO(
        @Positive Long userId,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Size(max = 5) List<@Valid AddressRequestDTO> addresses) {
}
