package com.showcase.common.domain.page;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;

/**
 * Request for a page of results, independent of Spring Data.
 * The persistence adapter converts it to a {@code PageRequest}.
 *
 * @param page      page number, starting at 0
 * @param size      elements per page, between 1 and {@link #MAX_SIZE}
 * @param sort      field to sort by (already validated against the resource's whitelist)
 * @param direction sort direction
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

    /** Offset of the page's first element, useful for manual queries. */
    public long offset() {
        return (long) page * size;
    }
}
