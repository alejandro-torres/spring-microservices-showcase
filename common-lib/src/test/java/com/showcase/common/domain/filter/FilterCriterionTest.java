package com.showcase.common.domain.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilterCriterionTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("crea el criterio cuando el número de valores encaja con el operador")
        void constructor_withMatchingArity_shouldCreateCriterion() {
            // when
            var criterion = FilterCriterion.of("price", FilterOperator.BETWEEN, new BigDecimal("100"), new BigDecimal("500"));

            // then
            assertThat(criterion.field()).isEqualTo("price");
            assertThat(criterion.values()).containsExactly(new BigDecimal("100"), new BigDecimal("500"));
        }

        @Test
        @DisplayName("acepta los operadores sin valor")
        void constructor_withNoValueOperator_shouldCreateCriterion() {
            assertThat(FilterCriterion.of("phone", FilterOperator.IS_NULL).values()).isEmpty();
        }

        @Test
        @DisplayName("rechaza un número de valores que no encaja con el operador")
        void constructor_withWrongArity_shouldThrowInvalidFilterValue() {
            assertThatThrownBy(() -> FilterCriterion.of("price", FilterOperator.BETWEEN, new BigDecimal("100")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_FILTER_VALUE);
        }

        @Test
        @DisplayName("rechaza campo u operador nulos")
        void constructor_withNulls_shouldFail() {
            assertThatThrownBy(() -> new FilterCriterion(null, FilterOperator.EQ, List.of(1)))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new FilterCriterion("name", null, List.of("a")))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("value")
    class Value {

        @Test
        @DisplayName("devuelve el primer valor o null si no hay valores")
        void value_shouldReturnFirstOrNull() {
            assertThat(FilterCriterion.of("status", FilterOperator.IN, "PENDING", "CONFIRMED").value()).isEqualTo("PENDING");
            assertThat(FilterCriterion.of("phone", FilterOperator.IS_NOT_NULL).value()).isNull();
        }
    }
}
