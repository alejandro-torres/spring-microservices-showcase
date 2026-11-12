package com.showcase.order.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Changes the shipping address of an order that is not closed. The lines cannot be modified: they have already
 * been sent to product-service to reserve their stock.
 */
public record UpdateOrderRequestDTO(
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String country,
        @NotNull @PositiveOrZero Long version) {
}
