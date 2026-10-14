package com.showcase.common.infrastructure.rest.filter;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidFilterException;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.filter.FilterOperator;
import com.showcase.common.domain.filter.FilterOperator.Arity;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Convierte los parámetros {@code filter=campo:operador:valor} de la URL en {@link FilterCriterion}
 * validados contra la lista blanca del recurso y con los valores ya tipados.
 *
 * <p>Formato: {@code campo:operador:valor}; para {@code in} y {@code between} los valores van
 * separados por comas, y {@code isNull} / {@code isNotNull} no llevan valor ({@code campo:isNull}).
 * Solo se parte por los dos primeros {@code :}, así que el valor puede contener {@code :} (fechas con hora).
 */
public class FilterCriteriaParser {

    public static final int DEFAULT_MAX_FILTERS = 10;

    private final int maxFilters;

    public FilterCriteriaParser() {
        this(DEFAULT_MAX_FILTERS);
    }

    public FilterCriteriaParser(int maxFilters) {
        if (maxFilters < 1) {
            throw new IllegalArgumentException("maxFilters debe ser al menos 1");
        }
        this.maxFilters = maxFilters;
    }

    public List<FilterCriterion> parse(List<String> rawFilters, FilterFields fields) {
        if (rawFilters == null || rawFilters.isEmpty()) {
            return List.of();
        }
        if (rawFilters.size() > maxFilters) {
            throw new InvalidFilterException(CommonErrorCode.TOO_MANY_FILTERS, maxFilters);
        }
        List<FilterCriterion> criteria = new ArrayList<>(rawFilters.size());
        for (String raw : rawFilters) {
            criteria.add(parseOne(raw, fields));
        }
        return List.copyOf(criteria);
    }

    private FilterCriterion parseOne(String raw, FilterFields fields) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidFilterException(CommonErrorCode.INVALID_FILTER_FORMAT, raw);
        }
        String[] parts = raw.split(":", 3);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new InvalidFilterException(CommonErrorCode.INVALID_FILTER_FORMAT, raw);
        }
        String fieldName = parts[0].trim();
        String operatorCode = parts[1].trim();
        String rawValue = parts.length == 3 ? parts[2] : null;

        FilterableField field = fields.find(fieldName)
                .orElseThrow(() -> new InvalidFilterException(
                        CommonErrorCode.FILTER_FIELD_NOT_ALLOWED, fieldName, String.join(", ", fields.fields().keySet())));

        FilterOperator operator = FilterOperator.fromCode(operatorCode)
                .filter(field::allows)
                .orElseThrow(() -> new InvalidFilterException(
                        CommonErrorCode.FILTER_OPERATOR_NOT_ALLOWED, operatorCode, fieldName, allowedCodes(field)));

        List<Object> values = convertValues(field, operator, rawValue);
        return new FilterCriterion(fieldName, operator, values);
    }

    private List<Object> convertValues(FilterableField field, FilterOperator operator, String rawValue) {
        if (operator.arity() == Arity.NONE) {
            if (rawValue != null && !rawValue.isBlank()) {
                throw invalidValue(field, rawValue, null);
            }
            return List.of();
        }
        if (rawValue == null || rawValue.isBlank()) {
            throw invalidValue(field, rawValue, null);
        }
        List<String> rawValues = switch (operator.arity()) {
            case TWO, MANY -> Arrays.asList(rawValue.split(",", -1));
            default -> List.of(rawValue);
        };
        if (!operator.arity().accepts(rawValues.size())) {
            throw invalidValue(field, rawValue, null);
        }
        List<Object> values = new ArrayList<>(rawValues.size());
        for (String value : rawValues) {
            values.add(convert(field, value));
        }
        if (operator == FilterOperator.BETWEEN) {
            checkRange(field, rawValue, values);
        }
        return values;
    }

    private Object convert(FilterableField field, String value) {
        try {
            return FilterValueConverter.convert(value, field.type());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalidValue(field, value, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void checkRange(FilterableField field, String rawValue, List<Object> values) {
        Comparable from = (Comparable) values.get(0);
        Comparable to = (Comparable) values.get(1);
        if (from.compareTo(to) > 0) {
            throw invalidValue(field, rawValue, null);
        }
    }

    private static InvalidFilterException invalidValue(FilterableField field, String value, Throwable cause) {
        return new InvalidFilterException(CommonErrorCode.INVALID_FILTER_VALUE, cause, field.name(), value);
    }

    private static String allowedCodes(FilterableField field) {
        return field.allowedOperators().stream()
                .sorted()
                .map(FilterOperator::code)
                .collect(Collectors.joining(", "));
    }
}
