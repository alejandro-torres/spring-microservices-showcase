package com.showcase.common.domain.page;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Domain page of results, independent of Spring Data.
 * The persistence adapter builds it from a {@code Page}.
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

    /** Computes the total pages and the first and last page flags. */
    public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean first = page == 0;
        boolean last = totalPages == 0 || page >= totalPages - 1;
        return new PageResult<>(content, page, size, totalElements, totalPages, first, last);
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return of(List.of(), query.page(), query.size(), 0);
    }

    /** Converts the content while keeping the pagination data (e.g. entity to domain model). */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = content.stream().<R>map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements, totalPages, first, last);
    }
}
