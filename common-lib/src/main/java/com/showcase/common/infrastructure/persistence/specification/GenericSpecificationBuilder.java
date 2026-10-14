package com.showcase.common.infrastructure.persistence.specification;

import com.showcase.common.domain.filter.FilterCriterion;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Convierte los {@link FilterCriterion} del dominio en una {@link Specification} de JPA que combina
 * todas las condiciones con AND. Se escribe una sola vez y la reutilizan todos los repositorios.
 *
 * <ul>
 *   <li>Los paths con punto ({@code category.name}, {@code roles.name}) se resuelven con joins; si el
 *       join es a una colección se aplica {@code DISTINCT} para no duplicar filas ni falsear el count.</li>
 *   <li>{@code like} y {@code startsWith} no distinguen mayúsculas ni tildes ({@code unaccent} de PostgreSQL)
 *       y escapan {@code %} y {@code _} del valor del usuario.</li>
 *   <li>Por defecto solo devuelve registros activos (borrado lógico).</li>
 * </ul>
 */
public class GenericSpecificationBuilder {

    static final String ACTIVE_FIELD = "active";
    private static final char ESCAPE = '\\';

    private final boolean useUnaccent;

    /** Con {@code unaccent} de PostgreSQL (la extensión la crean las migraciones de cada servicio). */
    public GenericSpecificationBuilder() {
        this(true);
    }

    public GenericSpecificationBuilder(boolean useUnaccent) {
        this.useUnaccent = useUnaccent;
    }

    public <T> Specification<T> build(List<FilterCriterion> criteria, FilterMapping mapping, boolean includeInactive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeInactive) {
                predicates.add(cb.isTrue(root.get(ACTIVE_FIELD)));
            }
            for (FilterCriterion criterion : criteria) {
                Path<Object> path = resolvePath(root, query, mapping.toPath(criterion.field()));
                predicates.add(toPredicate(criterion, path, cb));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate toPredicate(FilterCriterion criterion, Path<Object> path, CriteriaBuilder cb) {
        Expression<Comparable> comparable = (Expression) path;
        List<Object> values = criterion.values();
        return switch (criterion.operator()) {
            case EQ -> cb.equal(path, criterion.value());
            case NE -> cb.notEqual(path, criterion.value());
            case GT -> cb.greaterThan(comparable, (Comparable) criterion.value());
            case GTE -> cb.greaterThanOrEqualTo(comparable, (Comparable) criterion.value());
            case LT -> cb.lessThan(comparable, (Comparable) criterion.value());
            case LTE -> cb.lessThanOrEqualTo(comparable, (Comparable) criterion.value());
            case BETWEEN -> cb.between(comparable, (Comparable) values.get(0), (Comparable) values.get(1));
            case LIKE -> cb.like(normalized(path, cb), "%" + escape(normalize(criterion.value())) + "%", ESCAPE);
            case STARTS_WITH -> cb.like(normalized(path, cb), escape(normalize(criterion.value())) + "%", ESCAPE);
            case IN -> path.in(values);
            case IS_NULL -> cb.isNull(path);
            case IS_NOT_NULL -> cb.isNotNull(path);
        };
    }

    // Recorre el path creando joins para cada tramo intermedio (category.name → join category, get name).
    private static Path<Object> resolvePath(Root<?> root, CriteriaQuery<?> query, String dottedPath) {
        String[] segments = dottedPath.split("\\.");
        From<?, ?> from = root;
        for (int i = 0; i < segments.length - 1; i++) {
            Join<?, ?> join = from.join(segments[i], JoinType.INNER);
            if (join.getAttribute().isCollection()) {
                query.distinct(true);
            }
            from = join;
        }
        return from.get(segments[segments.length - 1]);
    }

    private Expression<String> normalized(Path<Object> path, CriteriaBuilder cb) {
        Expression<String> lower = cb.lower(path.as(String.class));
        return useUnaccent ? cb.function("unaccent", String.class, lower) : lower;
    }

    private String normalize(Object value) {
        String lower = String.valueOf(value).toLowerCase(Locale.ROOT);
        if (!useUnaccent) {
            return lower;
        }
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
