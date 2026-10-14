package com.showcase.order.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cambia la dirección de envío de un pedido que no está cerrado. Las líneas no se pueden modificar: ya se han
 * enviado a product-service para reservar su stock.
 */
public record UpdateOrderRequestDTO(
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String country,
        @NotNull @PositiveOrZero Long version) {
}
