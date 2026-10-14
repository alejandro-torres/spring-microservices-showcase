package com.showcase.common.infrastructure.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;

class StandardErrorResponsesTest {

    @Nested
    @DisplayName("forOperation")
    class ForOperation {

        @Test
        @DisplayName("GET por id: 401, 403, 404, 500 y 503")
        void get_byId_shouldIncludeNotFound() {
            assertThat(StandardErrorResponses.forOperation(RequestMethod.GET, true).keySet())
                    .containsExactly(401, 403, 404, 500, 503);
        }

        @Test
        @DisplayName("GET paginado: 400 (paginación o filtros) en lugar de 404")
        void get_list_shouldIncludeBadRequest() {
            assertThat(StandardErrorResponses.forOperation(RequestMethod.GET, false).keySet())
                    .containsExactly(400, 401, 403, 500, 503);
        }

        @Test
        @DisplayName("POST de creación: 400, 409 y 422 además de los comunes")
        void post_shouldIncludeValidationConflictAndBusinessRule() {
            assertThat(StandardErrorResponses.forOperation(RequestMethod.POST, false).keySet())
                    .containsExactly(400, 401, 403, 409, 422, 500, 503);
        }

        @Test
        @DisplayName("POST sobre un recurso existente también puede dar 404")
        void post_onExistingResource_shouldIncludeNotFound() {
            assertThat(StandardErrorResponses.forOperation(RequestMethod.POST, true)).containsKey(404);
        }

        @Test
        @DisplayName("PUT: 400, 404, 409 y 422; DELETE: 404 y 409")
        void putAndDelete_shouldFollowCrudTable() {
            assertThat(StandardErrorResponses.forOperation(RequestMethod.PUT, true).keySet())
                    .containsExactly(400, 401, 403, 404, 409, 422, 500, 503);
            assertThat(StandardErrorResponses.forOperation(RequestMethod.DELETE, true).keySet())
                    .containsExactly(401, 403, 404, 409, 500, 503);
        }

        @Test
        @DisplayName("cada código lleva una descripción")
        void everyStatus_shouldHaveDescription() {
            assertThat(StandardErrorResponses.forOperation(RequestMethod.PUT, true).values()).doesNotContainNull();
        }
    }
}
