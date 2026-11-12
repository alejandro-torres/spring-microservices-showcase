package com.showcase.common.infrastructure.persistence.specification;

import java.util.Map;

/**
 * Translates a filter's public name to the entity's JPA path (e.g. {@code categoryName}
 * to {@code category.name}). This way the API does not expose the entities' internal structure.
 */
@FunctionalInterface
public interface FilterMapping {

    String toPath(String field);

    /** Fields not in the map are used as they are. */
    static FilterMapping of(Map<String, String> paths) {
        Map<String, String> copy = Map.copyOf(paths);
        return field -> copy.getOrDefault(field, field);
    }

    static FilterMapping identity() {
        return field -> field;
    }
}
