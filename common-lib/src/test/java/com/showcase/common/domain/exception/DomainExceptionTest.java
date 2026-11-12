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
        @DisplayName("stores the error code and the message parameters")
        void constructor_withArgs_shouldKeepCodeAndArgs() {
            // when
            var exception = new ResourceNotFoundException(CommonErrorCode.RESOURCE_NOT_FOUND, "Product", 25L);

            // then
            assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
            assertThat(exception.getArgs()).containsExactly("Product", 25L);
        }

        @Test
        @DisplayName("generates a technical message with the code and parameters for the logs")
        void constructor_withArgs_shouldBuildTechnicalMessage() {
            // when
            var exception = new BusinessRuleException(CommonErrorCode.DATA_CONFLICT, 7);

            // then
            assertThat(exception).hasMessage("DATA_CONFLICT [7]");
        }

        @Test
        @DisplayName("uses only the code as the message when there are no parameters")
        void constructor_withoutArgs_shouldUseCodeAsMessage() {
            // when
            var exception = new ForbiddenException(CommonErrorCode.FORBIDDEN);

            // then
            assertThat(exception).hasMessage("FORBIDDEN");
            assertThat(exception.getArgs()).isEmpty();
        }

        @Test
        @DisplayName("keeps the original cause")
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
        @DisplayName("allows null parameters")
        void constructor_withNullArg_shouldAcceptIt() {
            // when
            var exception = new InvalidRequestException(CommonErrorCode.INVALID_PARAMETER, "sort", null);

            // then
            assertThat(exception.getArgs()).containsExactly("sort", null);
        }

        @Test
        @DisplayName("rejects a null error code")
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
        @DisplayName("returns a list that cannot be modified")
        void getArgs_shouldBeUnmodifiable() {
            // given
            var exception = new DuplicateResourceException(CommonErrorCode.DATA_CONFLICT, "name");

            // when / then
            assertThatThrownBy(() -> exception.getArgs().add("other"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("changes to the original array do not affect it")
        void getArgs_shouldBeIndependentFromOriginalArray() {
            // given
            Object[] args = {"original"};
            var exception = new DuplicateResourceException(CommonErrorCode.DATA_CONFLICT, args);

            // when
            args[0] = "changed";

            // then
            assertThat(exception.getArgs()).containsExactly("original");
        }
    }
}
