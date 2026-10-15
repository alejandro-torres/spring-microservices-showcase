package com.showcase.common.infrastructure.rest.page;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.SortDirection;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Construye el {@link PageQuery} a partir de los parámetros de la URL y valida que el campo de
 * ordenación esté en la lista blanca del recurso. Así un {@code sort} inventado devuelve 400 en vez
 * de llegar a la base de datos.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageQueryParser {

    public static PageQuery parse(int page, int size, String sort, String direction, Set<String> sortableFields) {
        String sortField = sort == null || sort.isBlank() ? PageQuery.DEFAULT_SORT : sort.trim();
        if (!sortableFields.contains(sortField)) {
            throw new InvalidRequestException(CommonErrorCode.INVALID_PAGINATION, "sort", sortField);
        }
        return new PageQuery(page, size, sortField, SortDirection.from(direction));
    }
}
