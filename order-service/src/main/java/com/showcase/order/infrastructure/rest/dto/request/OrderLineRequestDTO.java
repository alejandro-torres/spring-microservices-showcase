package com.showcase.order.infrastructure.rest.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderLineRequestDTO(
        @NotNull @Positive Long productId,
        @NotNull @Positive @Max(100) Integer quantity) {
}
