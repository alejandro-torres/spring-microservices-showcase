package com.showcase.common.domain.filter;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.util.List;
import java.util.Objects;

/**
 * Una condición de filtrado ya validada y con los valores convertidos a su tipo
 * (p. ej. {@code price BETWEEN 100 y 500} como {@code BigDecimal}).
 *
 * @param field    nombre público del campo, tal como aparece en la API
 * @param operator operador de comparación
 * @param values   valores tipados; vacío para {@code isNull} / {@code isNotNull}
 */
public record FilterCriterion(String field, FilterOperator operator, List<Object> values) {

    public FilterCriterion {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(operator, "operator");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        if (!operator.arity().accepts(values.size())) {
            throw new InvalidRequestException(CommonErrorCode.INVALID_FILTER_VALUE, field, operator.code());
        }
    }

    public static FilterCriterion of(String field, FilterOperator operator, Object... values) {
        return new FilterCriterion(field, operator, List.of(values));
    }

    /** Primer valor; útil para los operadores de un solo valor. */
    public Object value() {
        return values.isEmpty() ? null : values.getFirst();
    }
}
