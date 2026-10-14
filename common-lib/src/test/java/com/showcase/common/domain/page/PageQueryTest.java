package com.showcase.common.domain.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PageQueryTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("crea la consulta con los valores indicados")
        void constructor_withValidValues_shouldKeepThem() {
            // when
            var query = new PageQuery(2, 25, "price", SortDirection.DESC);

            // then
            assertThat(query.page()).isEqualTo(2);
            assertThat(query.size()).isEqualTo(25);
            assertThat(query.sort()).isEqualTo("price");
            assertThat(query.direction()).isEqualTo(SortDirection.DESC);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 100})
        @DisplayName("acepta los tamaños límite 1 y 100")
        void constructor_withBoundarySize_shouldAcceptIt(int size) {
            assertThat(new PageQuery(0, size, "id", SortDirection.ASC).size()).isEqualTo(size);
        }

        @ParameterizedTest
        @CsvSource({"-1, 10, page", "0, 0, size", "0, 101, size", "0, -5, size"})
        @DisplayName("rechaza una página negativa o un tamaño fuera de 1..100 con INVALID_PAGINATION")
        void constructor_withInvalidValues_shouldThrowInvalidPagination(int page, int size, String field) {
            assertThatThrownBy(() -> new PageQuery(page, size, "id", SortDirection.ASC))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_PAGINATION)
                    .satisfies(e -> assertThat(((InvalidRequestException) e).getArgs()).first().isEqualTo(field));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        @DisplayName("usa id como orden por defecto si no se indica")
        void constructor_withoutSort_shouldUseDefault(String sort) {
            assertThat(new PageQuery(0, 10, sort, SortDirection.ASC).sort()).isEqualTo(PageQuery.DEFAULT_SORT);
        }

        @Test
        @DisplayName("usa orden ascendente si no se indica la dirección")
        void constructor_withoutDirection_shouldUseAsc() {
            assertThat(new PageQuery(0, 10, "id", null).direction()).isEqualTo(SortDirection.ASC);
        }
    }

    @Nested
    @DisplayName("firstPage y offset")
    class FirstPageAndOffset {

        @Test
        @DisplayName("firstPage devuelve la página 0 de 10 elementos ordenada por id")
        void firstPage_shouldUseDefaults() {
            assertThat(PageQuery.firstPage()).isEqualTo(new PageQuery(0, 10, "id", SortDirection.ASC));
        }

        @Test
        @DisplayName("offset multiplica la página por el tamaño sin desbordar int")
        void offset_shouldMultiplyPageBySize() {
            assertThat(new PageQuery(3, 20, "id", SortDirection.ASC).offset()).isEqualTo(60);
            assertThat(new PageQuery(Integer.MAX_VALUE, 100, "id", SortDirection.ASC).offset())
                    .isEqualTo(Integer.MAX_VALUE * 100L);
        }
    }
}
