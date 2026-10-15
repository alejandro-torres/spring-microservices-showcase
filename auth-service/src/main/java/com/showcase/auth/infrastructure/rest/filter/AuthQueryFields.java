package com.showcase.auth.infrastructure.rest.filter;

import static com.showcase.common.domain.filter.FilterOperator.BETWEEN;
import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.GTE;
import static com.showcase.common.domain.filter.FilterOperator.IN;
import static com.showcase.common.domain.filter.FilterOperator.LIKE;
import static com.showcase.common.domain.filter.FilterOperator.LTE;
import static com.showcase.common.domain.filter.FilterOperator.STARTS_WITH;

import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.common.infrastructure.rest.filter.FilterFields;
import com.showcase.common.infrastructure.rest.filter.FilterableField;
import java.time.Instant;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Lista blanca de filtros y campos de ordenación de cada recurso. Lo que no aparece aquí
 * no se puede usar en {@code filter} ni en {@code sort} (p. ej. nunca el hash de la contraseña).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthQueryFields {

    private static final FilterableField CREATED_AT = FilterableField.of("createdAt", Instant.class, GTE, LTE, BETWEEN);

    public static final FilterFields USER_FILTERS = FilterFields.of(
            FilterableField.of("username", String.class, EQ, LIKE, STARTS_WITH),
            FilterableField.of("email", String.class, EQ, LIKE, STARTS_WITH),
            FilterableField.of("enabled", Boolean.class, EQ),
            FilterableField.of("role", String.class, EQ, IN),
            CREATED_AT);

    public static final Set<String> USER_SORT = Set.of("id", "username", "email", "createdAt", "updatedAt");

    public static final FilterFields ROLE_FILTERS = FilterFields.of(
            FilterableField.of("name", String.class, EQ, LIKE, STARTS_WITH),
            FilterableField.of("permission", String.class, EQ, IN),
            CREATED_AT);

    public static final Set<String> ROLE_SORT = Set.of("id", "name", "createdAt");

    public static final FilterFields PERMISSION_FILTERS = FilterFields.of(
            FilterableField.of("name", String.class, EQ, LIKE, STARTS_WITH),
            CREATED_AT);

    public static final Set<String> PERMISSION_SORT = Set.of("id", "name", "createdAt");

    public static final FilterFields AUDIT_FILTERS = FilterFields.of(
            FilterableField.of("eventType", SecurityEventType.class, EQ, IN),
            FilterableField.of("username", String.class, EQ, LIKE),
            FilterableField.of("ip", String.class, EQ),
            FilterableField.of("occurredAt", Instant.class, GTE, LTE, BETWEEN));

    public static final Set<String> AUDIT_SORT = Set.of("id", "occurredAt");
}
