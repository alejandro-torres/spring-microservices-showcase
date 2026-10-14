package com.showcase.common.domain.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SortDirectionTest {

    @ParameterizedTest
    @CsvSource({"asc, ASC", "ASC, ASC", "desc, DESC", "' Desc ', DESC"})
    @DisplayName("convierte el texto sin distinguir mayúsculas ni espacios")
    void from_withValidValue_shouldConvert(String value, SortDirection expected) {
        assertThat(SortDirection.from(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("usa ASC cuando no se indica")
    void from_withoutValue_shouldReturnAsc(String value) {
        assertThat(SortDirection.from(value)).isEqualTo(SortDirection.ASC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"up", "ascending", "1"})
    @DisplayName("rechaza valores desconocidos con INVALID_PAGINATION")
    void from_withInvalidValue_shouldThrow(String value) {
        assertThatThrownBy(() -> SortDirection.from(value))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_PAGINATION);
    }
}
