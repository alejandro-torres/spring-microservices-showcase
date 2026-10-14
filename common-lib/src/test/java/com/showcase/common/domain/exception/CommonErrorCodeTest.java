package com.showcase.common.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CommonErrorCodeTest {

    @ParameterizedTest
    @EnumSource(CommonErrorCode.class)
    @DisplayName("el código coincide con el nombre del enum y la clave del mensaje empieza por error.")
    void code_shouldMatchNameAndMessageKey(CommonErrorCode errorCode) {
        assertThat(errorCode.code()).isEqualTo(errorCode.name());
        assertThat(errorCode.messageKey()).isEqualTo("error." + errorCode.name());
        assertThat(errorCode.category()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = CommonErrorCode.class, names = {
            "INVALID_PAGINATION", "INVALID_FILTER_FORMAT", "FILTER_FIELD_NOT_ALLOWED",
            "FILTER_OPERATOR_NOT_ALLOWED", "INVALID_FILTER_VALUE", "TOO_MANY_FILTERS"})
    @DisplayName("los errores de paginación y filtros son peticiones inválidas")
    void paginationAndFilterCodes_shouldBeInvalidRequest(CommonErrorCode errorCode) {
        assertThat(errorCode.category()).isEqualTo(ErrorCategory.INVALID_REQUEST);
    }
}
