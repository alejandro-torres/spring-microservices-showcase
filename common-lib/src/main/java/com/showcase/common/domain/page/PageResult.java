package com.showcase.common.domain.page;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Página de resultados del dominio, independiente de Spring Data.
 * El adaptador de persistencia la construye a partir de un {@code Page}.
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public PageResult {
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    /** Calcula el total de páginas y los indicadores de primera y última página. */
    public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean first = page == 0;
        boolean last = totalPages == 0 || page >= totalPages - 1;
        return new PageResult<>(content, page, size, totalElements, totalPages, first, last);
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return of(List.of(), query.page(), query.size(), 0);
    }

    /** Convierte el contenido manteniendo los datos de paginación (p. ej. entidad a modelo de dominio). */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = content.stream().<R>map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements, totalPages, first, last);
    }
}
