package com.showcase.common.domain.page;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;

/**
 * Petición de una página de resultados, independiente de Spring Data.
 * El adaptador de persistencia la convierte en un {@code PageRequest}.
 *
 * @param page      número de página, empezando en 0
 * @param size      elementos por página, entre 1 y {@link #MAX_SIZE}
 * @param sort      campo por el que se ordena (ya validado contra la lista blanca del recurso)
 * @param direction sentido de la ordenación
 */
public record PageQuery(int page, int size, String sort, SortDirection direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT = "id";

    public PageQuery {
        if (page < 0) {
            throw new InvalidRequestException(CommonErrorCode.INVALID_PAGINATION, "page", page);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidRequestException(CommonErrorCode.INVALID_PAGINATION, "size", size);
        }
        if (sort == null || sort.isBlank()) {
            sort = DEFAULT_SORT;
        }
        if (direction == null) {
            direction = SortDirection.ASC;
        }
    }

    public static PageQuery firstPage() {
        return new PageQuery(DEFAULT_PAGE, DEFAULT_SIZE, DEFAULT_SORT, SortDirection.ASC);
    }

    /** Desplazamiento del primer elemento de la página, útil para consultas manuales. */
    public long offset() {
        return (long) page * size;
    }
}
