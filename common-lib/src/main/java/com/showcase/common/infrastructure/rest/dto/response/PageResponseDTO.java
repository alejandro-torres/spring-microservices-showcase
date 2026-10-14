package com.showcase.common.infrastructure.rest.dto.response;

import com.showcase.common.domain.page.PageResult;
import java.util.List;
import java.util.function.Function;

/**
 * Respuesta paginada de la API.
 */
public record PageResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public PageResponseDTO {
        content = List.copyOf(content);
    }

    /** Convierte un {@link PageResult} del dominio aplicando el mapper a cada elemento. */
    public static <D, T> PageResponseDTO<T> from(PageResult<D> result, Function<? super D, ? extends T> mapper) {
        List<T> content = result.content().stream().<T>map(mapper).toList();
        return new PageResponseDTO<>(content, result.page(), result.size(), result.totalElements(),
                result.totalPages(), result.first(), result.last());
    }
}
