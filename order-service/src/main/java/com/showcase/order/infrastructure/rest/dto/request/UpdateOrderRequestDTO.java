package com.showcase.order.infrastructure.rest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Sustituye todas las líneas de un pedido pendiente. */
public record UpdateOrderRequestDTO(
        @NotEmpty @Size(max = 20) List<@NotNull @Valid OrderLineRequestDTO> items,
        @NotNull @PositiveOrZero Long version) {
}
