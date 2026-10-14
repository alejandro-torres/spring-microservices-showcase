package com.showcase.common.infrastructure.persistence.page;

import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Traduce entre la paginación del dominio ({@link PageQuery}, {@link PageResult}) y la de
 * Spring Data ({@link Pageable}, {@link Page}). Solo lo usan los {@code *RepositoryImpl}.
 */
public final class SpringDataPageAdapter {

    private SpringDataPageAdapter() {
    }

    /** El campo de ordenación pasa por el mismo {@link FilterMapping} que los filtros (nombre público → path JPA). */
    public static Pageable toPageable(PageQuery query, FilterMapping mapping) {
        Sort.Direction direction = query.direction() == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, mapping.toPath(query.sort()));
        if (!"id".equals(query.sort())) {
            // Desempate estable: sin él, filas con el mismo valor pueden repetirse o saltarse entre páginas.
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(query.page(), query.size(), sort);
    }

    public static <E, D> PageResult<D> toPageResult(Page<E> page, Function<? super E, ? extends D> mapper) {
        return PageResult.of(page.getContent().stream().<D>map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
