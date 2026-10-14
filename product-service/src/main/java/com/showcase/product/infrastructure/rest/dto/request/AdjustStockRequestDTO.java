package com.showcase.product.infrastructure.rest.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code delta} positivo para entradas de inventario y negativo para salidas o mermas. */
public record AdjustStockRequestDTO(@NotNull @Min(-100000) @Max(100000) Integer delta) {
}
