package com.showcase.common.infrastructure.rest.filter;

import com.showcase.common.domain.filter.FilterOperator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Campo que un recurso permite filtrar: nombre público, tipo Java del valor y operadores admitidos.
 *
 * @param name             nombre del campo en la URL
 * @param type             tipo al que se convierte el valor (String, Long, BigDecimal, Instant, un enum...)
 * @param allowedOperators operadores que se pueden usar sobre este campo
 */
public record FilterableField(String name, Class<?> type, Set<FilterOperator> allowedOperators) {

    public FilterableField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (!FilterValueConverter.supports(type)) {
            throw new IllegalArgumentException("Tipo no soportado para filtros: " + type.getName());
        }
        if (allowedOperators == null || allowedOperators.isEmpty()) {
            throw new IllegalArgumentException("El campo " + name + " necesita al menos un operador");
        }
        for (FilterOperator operator : allowedOperators) {
            checkCompatible(name, type, operator);
        }
        allowedOperators = Set.copyOf(allowedOperators);
    }

    public static FilterableField of(String name, Class<?> type, FilterOperator first, FilterOperator... rest) {
        return new FilterableField(name, type, EnumSet.of(first, rest));
    }

    public boolean allows(FilterOperator operator) {
        return allowedOperators.contains(operator);
    }

    // Evita declarar por error operadores sin sentido, como like sobre un número.
    private static void checkCompatible(String name, Class<?> type, FilterOperator operator) {
        boolean compatible = switch (operator.valueKind()) {
            case ANY -> true;
            case TEXT -> type == String.class;
            case COMPARABLE -> Comparable.class.isAssignableFrom(type) && type != Boolean.class;
        };
        if (!compatible) {
            throw new IllegalArgumentException("El operador " + operator.code()
                    + " no se puede usar sobre el campo " + name + " de tipo " + type.getSimpleName());
        }
    }
}
