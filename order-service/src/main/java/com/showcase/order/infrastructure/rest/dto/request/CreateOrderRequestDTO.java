package com.showcase.order.infrastructure.rest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Solo productos y cantidades: el precio lo pone product-service y la dirección, customer-service. */
public record CreateOrderRequestDTO(
        @NotNull @Positive Long customerId,
        @NotEmpty @Size(max = 20) List<@NotNull @Valid OrderLineRequestDTO> items) {
}
