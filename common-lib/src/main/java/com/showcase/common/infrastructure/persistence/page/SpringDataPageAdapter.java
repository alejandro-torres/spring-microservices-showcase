package com.showcase.common.infrastructure.persistence.page;

import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Translates between the domain's pagination ({@link PageQuery}, {@link PageResult}) and
 * Spring Data's ({@link Pageable}, {@link Page}). Only the {@code *RepositoryImpl} classes use it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpringDataPageAdapter {

    /** The sort field goes through the same {@link FilterMapping} as the filters (public name → JPA path). */
    public static Pageable toPageable(PageQuery query, FilterMapping mapping) {
        Sort.Direction direction = query.direction() == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, mapping.toPath(query.sort()));
        if (!"id".equals(query.sort())) {
            // Stable tie-breaker: without it, rows with the same value can repeat or be skipped between pages.
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(query.page(), query.size(), sort);
    }

    public static <E, D> PageResult<D> toPageResult(Page<E> page, Function<? super E, ? extends D> mapper) {
        return PageResult.of(page.getContent().stream().<D>map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
