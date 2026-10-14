package com.showcase.common.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("guarda el código de error y los parámetros del mensaje")
        void constructor_withArgs_shouldKeepCodeAndArgs() {
            // when
            var exception = new ResourceNotFoundException(CommonErrorCode.RESOURCE_NOT_FOUND, "Product", 25L);

            // then
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
            assertThat(exception.getArgs()).containsExactly("Product", 25L);
        }

        @Test
        @DisplayName("genera un mensaje técnico con el código y los parámetros para los logs")
        void constructor_withArgs_shouldBuildTechnicalMessage() {
            // when
            var exception = new BusinessRuleException(CommonErrorCode.DATA_CONFLICT, 7);

            // then
            assertThat(exception).hasMessage("DATA_CONFLICT [7]");
        }

        @Test
        @DisplayName("usa solo el código como mensaje cuando no hay parámetros")
        void constructor_withoutArgs_shouldUseCodeAsMessage() {
            // when
            var exception = new ForbiddenException(CommonErrorCode.FORBIDDEN);

            // then
            assertThat(exception).hasMessage("FORBIDDEN");
            assertThat(exception.getArgs()).isEmpty();
        }

        @Test
        @DisplayName("conserva la causa original")
        void constructor_withCause_shouldKeepCause() {
            // given
            var cause = new IllegalStateException("timeout");

            // when
            var exception = new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE, cause, "product-service");

            // then
            assertThat(exception).hasCause(cause);
            assertThat(exception.getArgs()).containsExactly("product-service");
        }

        @Test
        @DisplayName("admite parámetros nulos")
        void constructor_withNullArg_shouldAcceptIt() {
            // when
            var exception = new InvalidRequestException(CommonErrorCode.INVALID_PARAMETER, "sort", null);

            // then
            assertThat(exception.getArgs()).containsExactly("sort", null);
        }

        @Test
        @DisplayName("rechaza un código de error nulo")
        void constructor_withNullCode_shouldFail() {
            assertThatThrownBy(() -> new UnauthorizedException(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("errorCode");
        }
    }

    @Nested
    @DisplayName("getArgs")
    class GetArgs {

        @Test
        @DisplayName("devuelve una lista que no se puede modificar")
        void getArgs_shouldBeUnmodifiable() {
            // given
            var exception = new DuplicateResourceException(CommonErrorCode.DATA_CONFLICT, "name");

            // when / then
            assertThatThrownBy(() -> exception.getArgs().add("otro"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("no le afectan los cambios en el array original")
        void getArgs_shouldBeIndependentFromOriginalArray() {
            // given
            Object[] args = {"original"};
            var exception = new DuplicateResourceException(CommonErrorCode.DATA_CONFLICT, args);

            // when
            args[0] = "cambiado";

            // then
            assertThat(exception.getArgs()).containsExactly("original");
        }
    }
}
