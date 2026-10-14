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

        @ParameterizedTest(name = "página {0} de {1} elementos con total {2} → {3} páginas")
        @CsvSource({
                "0, 10, 57, 6, true, false",
                "5, 10, 57, 6, false, true",
                "2, 10, 57, 6, false, false",
                "0, 10, 10, 1, true, true",
                "0, 10, 0, 0, true, true",
                "1, 10, 20, 2, false, true"
        })
        @DisplayName("calcula el total de páginas y los indicadores de primera y última")
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
        @DisplayName("hace una copia que no se puede modificar del contenido")
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
        @DisplayName("rechaza un contenido nulo")
        void of_withNullContent_shouldFail() {
            assertThatThrownBy(() -> PageResult.of(null, 0, 10, 0)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("empty")
    class Empty {

        @Test
        @DisplayName("devuelve una página vacía con la página y el tamaño de la consulta")
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
        @DisplayName("convierte el contenido y conserva los datos de paginación")
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
