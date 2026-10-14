package com.showcase.common.infrastructure.rest.filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lista blanca de campos filtrables de un recurso. Cada recurso declara la suya
 * (p. ej. {@code ProductFilterFields}); lo que no está aquí no se puede filtrar.
 */
public interface FilterFields {

    /** Campos filtrables indexados por su nombre público. */
    Map<String, FilterableField> fields();

    default Optional<FilterableField> find(String name) {
        return Optional.ofNullable(fields().get(name));
    }

    static FilterFields of(FilterableField... fields) {
        Map<String, FilterableField> byName = Arrays.stream(fields)
                .collect(Collectors.toMap(FilterableField::name, Function.identity(),
                        (a, b) -> {
                            throw new IllegalArgumentException("Campo filtrable repetido: " + a.name());
                        },
                        LinkedHashMap::new));
        Map<String, FilterableField> unmodifiable = Collections.unmodifiableMap(byName);
        return () -> unmodifiable;
    }
}
