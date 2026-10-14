package com.showcase.common.infrastructure.rest.filter;

import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.GTE;
import static com.showcase.common.domain.filter.FilterOperator.IN;
import static com.showcase.common.domain.filter.FilterOperator.LIKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilterableFieldTest {

    @Nested
    @DisplayName("FilterableField")
    class Field {

        @Test
        @DisplayName("indica qué operadores admite el campo")
        void allows_shouldCheckAllowedOperators() {
            var field = FilterableField.of("price", BigDecimal.class, EQ, GTE);
            assertThat(field.allows(GTE)).isTrue();
            assertThat(field.allows(IN)).isFalse();
        }

        @Test
        @DisplayName("rechaza operadores incompatibles con el tipo del campo")
        void constructor_withIncompatibleOperator_shouldFail() {
            assertThatThrownBy(() -> FilterableField.of("price", BigDecimal.class, LIKE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("like");
            assertThatThrownBy(() -> FilterableField.of("active", Boolean.class, GTE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rechaza tipos no soportados y campos sin operadores")
        void constructor_withUnsupportedTypeOrNoOperators_shouldFail() {
            assertThatThrownBy(() -> FilterableField.of("tags", Set.class, EQ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FilterableField("name", String.class, Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("FilterFields.of")
    class Fields {

        @Test
        @DisplayName("indexa los campos por nombre y los busca")
        void of_shouldIndexByName() {
            var fields = FilterFields.of(FilterableField.of("name", String.class, EQ));
            assertThat(fields.find("name")).isPresent();
            assertThat(fields.find("price")).isEmpty();
        }

        @Test
        @DisplayName("rechaza campos repetidos")
        void of_withDuplicatedField_shouldFail() {
            assertThatThrownBy(() -> FilterFields.of(
                    FilterableField.of("name", String.class, EQ),
                    FilterableField.of("name", String.class, LIKE)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }
    }
}
