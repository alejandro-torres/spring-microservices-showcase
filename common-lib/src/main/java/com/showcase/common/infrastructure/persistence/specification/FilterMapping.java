package com.showcase.common.infrastructure.persistence.specification;

import java.util.Map;

/**
 * Traduce el nombre público de un filtro al path JPA de la entidad (p. ej. {@code categoryName}
 * a {@code category.name}). Así la API no expone la estructura interna de las entidades.
 */
@FunctionalInterface
public interface FilterMapping {

    String toPath(String field);

    /** Los campos que no aparecen en el mapa se usan tal cual. */
    static FilterMapping of(Map<String, String> paths) {
        Map<String, String> copy = Map.copyOf(paths);
        return field -> copy.getOrDefault(field, field);
    }

    static FilterMapping identity() {
        return field -> field;
    }
}
