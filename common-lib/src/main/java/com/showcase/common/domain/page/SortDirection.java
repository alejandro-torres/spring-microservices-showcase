package com.showcase.common.domain.page;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.util.Locale;

public enum SortDirection {
    ASC,
    DESC;

    /** Converts the URL parameter case-insensitively; if invalid it throws INVALID_PAGINATION. */
    public static SortDirection from(String value) {
        if (value == null || value.isBlank()) {
            return ASC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(CommonErrorCode.INVALID_PAGINATION, e, "direction", value);
        }
    }
}
