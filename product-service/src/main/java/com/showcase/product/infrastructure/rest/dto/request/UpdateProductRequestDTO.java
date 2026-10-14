package com.showcase.product.infrastructure.rest.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** El stock no se cambia aquí: tiene su propio endpoint de ajuste. */
public record UpdateProductRequestDTO(
        @NotBlank @Size(min = 2, max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal price,
        @NotNull @Positive Long categoryId,
        @NotNull @PositiveOrZero Long version) {
}
