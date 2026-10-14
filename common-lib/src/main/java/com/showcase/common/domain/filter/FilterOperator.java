package com.showcase.common.domain.filter;

import java.util.Arrays;
import java.util.Optional;

/**
 * Operadores de los filtros dinámicos ({@code ?filter=campo:operador:valor}).
 */
public enum FilterOperator {

    EQ("eq", Arity.ONE, ValueKind.ANY),
    NE("ne", Arity.ONE, ValueKind.ANY),
    GT("gt", Arity.ONE, ValueKind.COMPARABLE),
    GTE("gte", Arity.ONE, ValueKind.COMPARABLE),
    LT("lt", Arity.ONE, ValueKind.COMPARABLE),
    LTE("lte", Arity.ONE, ValueKind.COMPARABLE),
    BETWEEN("between", Arity.TWO, ValueKind.COMPARABLE),
    LIKE("like", Arity.ONE, ValueKind.TEXT),
    STARTS_WITH("startsWith", Arity.ONE, ValueKind.TEXT),
    IN("in", Arity.MANY, ValueKind.ANY),
    IS_NULL("isNull", Arity.NONE, ValueKind.ANY),
    IS_NOT_NULL("isNotNull", Arity.NONE, ValueKind.ANY);

    /** Cuántos valores admite el operador. */
    public enum Arity {
        NONE, ONE, TWO, MANY;

        public boolean accepts(int count) {
            return switch (this) {
                case NONE -> count == 0;
                case ONE -> count == 1;
                case TWO -> count == 2;
                case MANY -> count >= 1;
            };
        }
    }

    /** Sobre qué tipos de campo tiene sentido el operador. */
    public enum ValueKind {
        ANY, COMPARABLE, TEXT
    }

    private final String code;
    private final Arity arity;
    private final ValueKind valueKind;

    FilterOperator(String code, Arity arity, ValueKind valueKind) {
        this.code = code;
        this.arity = arity;
        this.valueKind = valueKind;
    }

    /** Nombre del operador en la URL, p. ej. {@code gte} o {@code startsWith}. */
    public String code() {
        return code;
    }

    public Arity arity() {
        return arity;
    }

    public ValueKind valueKind() {
        return valueKind;
    }

    /** Busca el operador por su nombre en la URL, sin distinguir mayúsculas. */
    public static Optional<FilterOperator> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(op -> op.code.equalsIgnoreCase(code.trim())).findFirst();
    }
}
