package com.showcase.product.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequestDTO(
        @NotBlank @Size(min = 2, max = 60) String name,
        @Size(max = 255) String description,
        @NotNull @PositiveOrZero Long version) {
}
