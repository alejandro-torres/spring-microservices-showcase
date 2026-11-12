package com.showcase.common.infrastructure.rest.filter;

import java.util.List;

/**
 * Raw values of the URL's {@code filter} parameters, one per {@code filter=...}.
 *
 * <p>Used instead of {@code @RequestParam List<String>} because Spring splits a single parameter
 * by commas, which would break filters with several values ({@code categoryId:in:1,2,3}).
 * It is filled in by {@link FilterParametersArgumentResolver}.
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
