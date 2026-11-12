package com.showcase.common.domain.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageResultTest {

    @Nested
    @DisplayName("of")
    class Of {

        @ParameterizedTest(name = "page {0} of {1} elements with total {2} → {3} pages")
        @CsvSource({
                "0, 10, 57, 6, true, false",
                "5, 10, 57, 6, false, true",
                "2, 10, 57, 6, false, false",
                "0, 10, 10, 1, true, true",
                "0, 10, 0, 0, true, true",
                "1, 10, 20, 2, false, true"
        })
        @DisplayName("computes the total pages and the first and last flags")
        void of_shouldComputePagination(int page, int size, long total, int expectedPages,
                                        boolean expectedFirst, boolean expectedLast) {
            // when
            var result = PageResult.of(List.of(), page, size, total);

            // then
            assertThat(result.totalPages()).isEqualTo(expectedPages);
            assertThat(result.first()).isEqualTo(expectedFirst);
            assertThat(result.last()).isEqualTo(expectedLast);
            assertThat(result.totalElements()).isEqualTo(total);
        }

        @Test
        @DisplayName("makes an unmodifiable copy of the content")
        void of_shouldCopyContent() {
            // given
            var content = new ArrayList<>(List.of("a", "b"));

            // when
            var result = PageResult.of(content, 0, 10, 2);
            content.add("c");

            // then
            assertThat(result.content()).containsExactly("a", "b");
            assertThatThrownBy(() -> result.content().add("d")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("rejects null content")
        void of_withNullContent_shouldFail() {
            assertThatThrownBy(() -> PageResult.of(null, 0, 10, 0)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("empty")
    class Empty {

        @Test
        @DisplayName("returns an empty page with the query's page and size")
        void empty_shouldUseQueryPageAndSize() {
            // when
            var result = PageResult.<String>empty(new PageQuery(3, 20, "id", SortDirection.ASC));

            // then
            assertThat(result.content()).isEmpty();
            assertThat(result.page()).isEqualTo(3);
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalElements()).isZero();
            assertThat(result.totalPages()).isZero();
        }
    }

    @Nested
    @DisplayName("map")
    class Map {

        @Test
        @DisplayName("converts the content and keeps the pagination data")
        void map_shouldConvertContentAndKeepPagination() {
            // given
            var result = PageResult.of(List.of(1, 2, 3), 1, 3, 9);

            // when
            var mapped = result.map(n -> "n" + n);

            // then
            assertThat(mapped.content()).containsExactly("n1", "n2", "n3");
            assertThat(mapped).usingRecursiveComparison().ignoringFields("content").isEqualTo(result);
        }
    }
}
