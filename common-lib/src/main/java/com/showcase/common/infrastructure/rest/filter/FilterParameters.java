package com.showcase.common.infrastructure.rest.filter;

import java.util.List;

/**
 * Valores crudos de los parámetros {@code filter} de la URL, uno por cada {@code filter=...}.
 *
 * <p>Se usa en lugar de {@code @RequestParam List<String>} porque Spring separa por comas un único
 * parámetro, y eso rompería los filtros con varios valores ({@code categoryId:in:1,2,3}).
 * Lo rellena {@link FilterParametersArgumentResolver}.
 */
public record FilterParameters(List<String> values) {

    public static final String PARAMETER_NAME = "filter";

    public FilterParameters {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public static FilterParameters none() {
        return new FilterParameters(List.of());
    }
}
