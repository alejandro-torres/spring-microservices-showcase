package com.showcase.common.infrastructure.rest.filter;

import com.showcase.common.domain.filter.FilterOperator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Field that a resource allows filtering on: public name, Java type of the value and supported operators.
 *
 * @param name             name of the field in the URL
 * @param type             type the value is converted to (String, Long, BigDecimal, Instant, an enum...)
 * @param allowedOperators operators that can be used on this field
 */
public record FilterableField(String name, Class<?> type, Set<FilterOperator> allowedOperators) {

    public FilterableField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (!FilterValueConverter.supports(type)) {
            throw new IllegalArgumentException("Type not supported for filters: " + type.getName());
        }
        if (allowedOperators == null || allowedOperators.isEmpty()) {
            throw new IllegalArgumentException("Field " + name + " needs at least one operator");
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

    // Prevents declaring meaningless operators by mistake, such as like on a number.
    private static void checkCompatible(String name, Class<?> type, FilterOperator operator) {
        boolean compatible = switch (operator.valueKind()) {
            case ANY -> true;
            case TEXT -> type == String.class;
            case COMPARABLE -> Comparable.class.isAssignableFrom(type) && type != Boolean.class;
        };
        if (!compatible) {
            throw new IllegalArgumentException("Operator " + operator.code()
                    + " cannot be used on field " + name + " of type " + type.getSimpleName());
        }
    }
}
