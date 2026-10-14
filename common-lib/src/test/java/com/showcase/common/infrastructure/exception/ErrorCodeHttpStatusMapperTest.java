package com.showcase.common.infrastructure.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ErrorCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorCodeHttpStatusMapperTest {

    private final ErrorCodeHttpStatusMapper mapper = new ErrorCodeHttpStatusMapper();

    @ParameterizedTest
    @CsvSource({
            "INVALID_REQUEST, 400", "UNAUTHORIZED, 401", "FORBIDDEN, 403", "NOT_FOUND, 404",
            "CONFLICT, 409", "LOCKED, 423", "BUSINESS_RULE, 422", "RATE_LIMITED, 429",
            "EXTERNAL_SERVICE, 503", "INTERNAL, 500"
    })
    @DisplayName("traduce cada categoría de error a su código HTTP estándar")
    void toStatus_shouldMapEachCategory(ErrorCategory category, int expectedStatus) {
        assertThat(mapper.toStatus(category).value()).isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @EnumSource(CommonErrorCode.class)
    @DisplayName("solo devuelve errores 4xx o 5xx")
    void toStatus_shouldAlwaysReturnErrorStatus(CommonErrorCode errorCode) {
        assertThat(mapper.toStatus(errorCode).isError()).isTrue();
    }

    @Test
    @DisplayName("usa la categoría del código de error")
    void toStatus_withErrorCode_shouldUseItsCategory() {
        assertThat(mapper.toStatus(CommonErrorCode.CONCURRENT_MODIFICATION).value()).isEqualTo(409);
    }
}
