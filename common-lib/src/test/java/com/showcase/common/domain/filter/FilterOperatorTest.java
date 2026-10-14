package com.showcase.common.domain.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.common.domain.filter.FilterOperator.Arity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FilterOperatorTest {

    @Nested
    @DisplayName("fromCode")
    class FromCode {

        @ParameterizedTest
        @CsvSource({"eq, EQ", "gte, GTE", "between, BETWEEN", "startsWith, STARTS_WITH",
                "STARTSWITH, STARTS_WITH", "isNotNull, IS_NOT_NULL", "' like ', LIKE"})
        @DisplayName("encuentra el operador por su nombre en la URL sin distinguir mayúsculas")
        void fromCode_withKnownCode_shouldReturnOperator(String code, FilterOperator expected) {
            assertThat(FilterOperator.fromCode(code)).contains(expected);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"equals", ">=", "starts_with"})
        @DisplayName("devuelve vacío para un operador desconocido")
        void fromCode_withUnknownCode_shouldReturnEmpty(String code) {
            assertThat(FilterOperator.fromCode(code)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Arity.accepts")
    class Accepts {

        @ParameterizedTest
        @CsvSource({
                "NONE, 0, true", "NONE, 1, false",
                "ONE, 1, true", "ONE, 0, false", "ONE, 2, false",
                "TWO, 2, true", "TWO, 1, false", "TWO, 3, false",
                "MANY, 1, true", "MANY, 5, true", "MANY, 0, false"
        })
        @DisplayName("comprueba el número de valores que admite cada operador")
        void accepts_shouldCheckValueCount(Arity arity, int count, boolean expected) {
            assertThat(arity.accepts(count)).isEqualTo(expected);
        }
    }
}
