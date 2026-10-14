package com.showcase.common.infrastructure.rest.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.common.domain.page.PageResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PageResponseDTOTest {

    @Nested
    @DisplayName("from")
    class From {

        @Test
        @DisplayName("convierte el contenido y copia los datos de paginación del PageResult")
        void from_shouldMapContentAndCopyPagination() {
            // given
            var result = PageResult.of(List.of(1, 2), 2, 2, 7);

            // when
            var response = PageResponseDTO.from(result, n -> "item-" + n);

            // then
            assertThat(response.content()).containsExactly("item-1", "item-2");
            assertThat(response.page()).isEqualTo(2);
            assertThat(response.size()).isEqualTo(2);
            assertThat(response.totalElements()).isEqualTo(7);
            assertThat(response.totalPages()).isEqualTo(4);
            assertThat(response.first()).isFalse();
            assertThat(response.last()).isFalse();
        }
    }

    @Nested
    @DisplayName("ErrorResponseDTO")
    class ErrorResponse {

        @Test
        @DisplayName("usa una lista vacía cuando no hay errores por campo")
        void constructor_withNullErrors_shouldUseEmptyList() {
            var error = new ErrorResponseDTO(null, 404, "Not Found", "X", "m", "/p", null, null);
            assertThat(error.errors()).isEmpty();
        }
    }
}
