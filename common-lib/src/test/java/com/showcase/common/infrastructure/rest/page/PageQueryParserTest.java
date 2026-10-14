package com.showcase.common.infrastructure.rest.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.SortDirection;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PageQueryParserTest {

    private static final Set<String> SORTABLE = Set.of("id", "username", "createdAt");

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("crea el PageQuery con un campo de ordenación permitido")
        void parse_withAllowedSort_shouldCreateQuery() {
            assertThat(PageQueryParser.parse(1, 20, "username", "desc", SORTABLE))
                    .isEqualTo(new PageQuery(1, 20, "username", SortDirection.DESC));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        @DisplayName("usa id si no se indica el orden")
        void parse_withoutSort_shouldUseId(String sort) {
            assertThat(PageQueryParser.parse(0, 10, sort, null, SORTABLE).sort()).isEqualTo("id");
        }

        @ParameterizedTest
        @ValueSource(strings = {"passwordHash", "email", "1; DROP TABLE users"})
        @DisplayName("rechaza un campo de ordenación fuera de la lista blanca con INVALID_PAGINATION")
        void parse_withSortNotAllowed_shouldThrow(String sort) {
            assertThatThrownBy(() -> PageQueryParser.parse(0, 10, sort, "asc", SORTABLE))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_PAGINATION);
        }
    }
}
