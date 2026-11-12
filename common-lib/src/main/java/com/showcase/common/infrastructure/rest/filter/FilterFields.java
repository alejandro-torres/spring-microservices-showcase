package com.showcase.common.infrastructure.rest.filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Whitelist of a resource's filterable fields. Each resource declares its own
 * (e.g. {@code ProductFilterFields}); anything not here cannot be filtered.
 */
public interface FilterFields {

    /** Filterable fields indexed by their public name. */
    Map<String, FilterableField> fields();

    default Optional<FilterableField> find(String name) {
        return Optional.ofNullable(fields().get(name));
    }

    static FilterFields of(FilterableField... fields) {
        Map<String, FilterableField> byName = Arrays.stream(fields)
                .collect(Collectors.toMap(FilterableField::name, Function.identity(),
                        (a, b) -> {
                            throw new IllegalArgumentException("Repeated filterable field: " + a.name());
                        },
                        LinkedHashMap::new));
        Map<String, FilterableField> unmodifiable = Collections.unmodifiableMap(byName);
        return () -> unmodifiable;
    }
}
