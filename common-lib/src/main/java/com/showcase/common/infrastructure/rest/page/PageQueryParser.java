package com.showcase.common.infrastructure.rest.page;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.SortDirection;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builds the {@link PageQuery} from the URL parameters and validates that the sort field is on the
 * resource's whitelist. This way an invented {@code sort} returns 400 instead of reaching the database.
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
