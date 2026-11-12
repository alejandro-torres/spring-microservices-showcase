package com.showcase.common.domain.filter;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.util.List;
import java.util.Objects;

/**
 * A filter condition already validated and with its values converted to their type
 * (e.g. {@code price BETWEEN 100 and 500} as {@code BigDecimal}).
 *
 * @param field    public name of the field, as it appears in the API
 * @param operator comparison operator
 * @param values   typed values; empty for {@code isNull} / {@code isNotNull}
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

    /** First value; useful for single-value operators. */
    public Object value() {
        return values.isEmpty() ? null : values.getFirst();
    }
}
