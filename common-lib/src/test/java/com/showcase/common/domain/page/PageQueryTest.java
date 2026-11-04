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
        @DisplayName("creates the query with the given values")
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
        @DisplayName("accepts the boundary sizes 1 and 100")
        void constructor_withBoundarySize_shouldAcceptIt(int size) {
            assertThat(new PageQuery(0, size, "id", SortDirection.ASC).size()).isEqualTo(size);
        }

        @ParameterizedTest
        @CsvSource({"-1, 10, page", "0, 0, size", "0, 101, size", "0, -5, size"})
        @DisplayName("rejects a negative page or a size outside 1..100 with INVALID_PAGINATION")
        void constructor_withInvalidValues_shouldThrowInvalidPagination(int page, int size, String field) {
            assertThatThrownBy(() -> new PageQuery(page, size, "id", SortDirection.ASC))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_PAGINATION)
                    .satisfies(e -> assertThat(((InvalidRequestException) e).getArgs()).first().isEqualTo(field));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        @DisplayName("uses id as the default sort if not specified")
        void constructor_withoutSort_shouldUseDefault(String sort) {
            assertThat(new PageQuery(0, 10, sort, SortDirection.ASC).sort()).isEqualTo(PageQuery.DEFAULT_SORT);
        }

        @Test
        @DisplayName("uses ascending order if the direction is not specified")
        void constructor_withoutDirection_shouldUseAsc() {
            assertThat(new PageQuery(0, 10, "id", null).direction()).isEqualTo(SortDirection.ASC);
        }
    }

    @Nested
    @DisplayName("firstPage and offset")
    class FirstPageAndOffset {

        @Test
        @DisplayName("firstPage returns page 0 with 10 elements sorted by id")
        void firstPage_shouldUseDefaults() {
            assertThat(PageQuery.firstPage()).isEqualTo(new PageQuery(0, 10, "id", SortDirection.ASC));
        }

        @Test
        @DisplayName("offset multiplies the page by the size without overflowing int")
        void offset_shouldMultiplyPageBySize() {
            assertThat(new PageQuery(3, 20, "id", SortDirection.ASC).offset()).isEqualTo(60);
            assertThat(new PageQuery(Integer.MAX_VALUE, 100, "id", SortDirection.ASC).offset())
                    .isEqualTo(Integer.MAX_VALUE * 100L);
        }
    }
}
