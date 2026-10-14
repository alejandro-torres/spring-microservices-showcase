package com.showcase.common.infrastructure.rest.filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Convierte el texto de un filtro al tipo Java del campo.
 */
final class FilterValueConverter {

    private static final Set<Class<?>> SUPPORTED = Set.of(
            String.class, Long.class, Integer.class, BigDecimal.class, Boolean.class,
            Instant.class, LocalDate.class, UUID.class);

    private FilterValueConverter() {
    }

    static boolean supports(Class<?> type) {
        return SUPPORTED.contains(type) || type.isEnum();
    }

    /**
     * @throws IllegalArgumentException si el texto no representa un valor válido del tipo
     */
    static Object convert(String raw, Class<?> type) {
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("valor vacío");
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
        throw new IllegalArgumentException("tipo no soportado: " + type.getName());
    }

    // Boolean.parseBoolean convierte cualquier texto en false; aquí solo valen true y false.
    private static Boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("no es un booleano: " + value);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseEnum(String value, Class<?> type) {
        return Enum.valueOf((Class<? extends Enum>) type, value.toUpperCase(Locale.ROOT));
    }
}
