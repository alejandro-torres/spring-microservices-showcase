package com.showcase.common.infrastructure.rest.filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Converts the text of a filter to the Java type of the field.
 */
final class FilterValueConverter {

    private static final Set<Class<?>> SUPPORTED = Set.of(
            String.class, Long.class, Integer.class, BigDecimal.class, Boolean.class,
            Instant.class, LocalDate.class, UUID.class);

    static boolean supports(Class<?> type) {
        return SUPPORTED.contains(type) || type.isEnum();
    }

    /**
     * @throws IllegalArgumentException if the text does not represent a valid value of the type
     */
    static Object convert(String raw, Class<?> type) {
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("empty value");
        }
        if (type == String.class) {
            return value;
        }
        if (type == Long.class) {
            return Long.valueOf(value);
        }
        if (type == Integer.class) {
            return Integer.valueOf(value);
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(value);
        }
        if (type == Boolean.class) {
            return parseBoolean(value);
        }
        if (type == Instant.class) {
            return Instant.parse(value);
        }
        if (type == LocalDate.class) {
            return LocalDate.parse(value);
        }
        if (type == UUID.class) {
            return UUID.fromString(value);
        }
        if (type.isEnum()) {
            return parseEnum(value, type);
        }
        throw new IllegalArgumentException("unsupported type: " + type.getName());
    }

    // Boolean.parseBoolean turns any text into false; here only true and false are valid.
    private static Boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("is not a boolean: " + value);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseEnum(String value, Class<?> type) {
        return Enum.valueOf((Class<? extends Enum>) type, value.toUpperCase(Locale.ROOT));
    }
}
