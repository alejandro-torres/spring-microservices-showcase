package com.showcase.common.infrastructure.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ErrorCategory;
import com.showcase.common.domain.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.StaticMessageSource;

class ErrorMessageResolverTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private final StaticMessageSource serviceMessages = new StaticMessageSource();
    private final ErrorMessageResolver resolver = new ErrorMessageResolver(serviceMessages);

    private enum TestErrorCode implements ErrorCode {
        PRODUCT_NOT_FOUND, UNKNOWN_CODE;

        @Override
        public String code() {
            return name();
        }

        @Override
        public ErrorCategory category() {
            return ErrorCategory.NOT_FOUND;
        }
    }

    @Nested
    @DisplayName("resolve with an error code")
    class ResolveErrorCode {

        @Test
        @DisplayName("returns the common message in English with its parameters")
        void resolve_withEnglish_shouldReturnEnglishMessage() {
            var message = resolver.resolve(CommonErrorCode.TOO_MANY_FILTERS, List.of(10), Locale.US);
            assertThat(message).isEqualTo("At most 10 filters are allowed per request.");
        }

        @Test
        @DisplayName("returns the common message in Spanish")
        void resolve_withSpanish_shouldReturnSpanishMessage() {
            var message = resolver.resolve(CommonErrorCode.INVALID_FILTER_VALUE, List.of("price", "abc"), SPANISH);
            assertThat(message).isEqualTo("El valor 'abc' no es válido para el filtro del campo 'price'.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"fr", "de-DE", "pt"})
        @DisplayName("uses English for unsupported languages")
        void resolve_withUnsupportedLanguage_shouldFallBackToEnglish(String languageTag) {
            var message = resolver.resolve(CommonErrorCode.FORBIDDEN, List.of(), Locale.forLanguageTag(languageTag));
            assertThat(message).isEqualTo("You do not have permission to perform this operation.");
        }

        @Test
        @DisplayName("gives priority to the service's message over the common one")
        void resolve_withServiceMessage_shouldUseIt() {
            serviceMessages.addMessage("error.FORBIDDEN", Locale.ENGLISH, "Only an administrator can do this.");
            assertThat(resolver.resolve(CommonErrorCode.FORBIDDEN, List.of(), Locale.ENGLISH))
                    .isEqualTo("Only an administrator can do this.");
        }

        @Test
        @DisplayName("resolves a service's own codes")
        void resolve_withServiceErrorCode_shouldUseServiceMessage() {
            serviceMessages.addMessage("error.PRODUCT_NOT_FOUND", Locale.ENGLISH, "Product with id {0} not found");
            assertThat(resolver.resolve(TestErrorCode.PRODUCT_NOT_FOUND, List.of(25), Locale.ENGLISH))
                    .isEqualTo("Product with id 25 not found");
        }

        @Test
        @DisplayName("does not format the numbers in parameters (ids without thousands separators)")
        void resolve_withLargeNumber_shouldNotFormatIt() {
            var message = resolver.resolve(CommonErrorCode.RESOURCE_NOT_FOUND, List.of("Product", 25000L), Locale.ENGLISH);
            assertThat(message).isEqualTo("Product with id 25000 was not found.");
        }

        @Test
        @DisplayName("uses a generic message if the code has no translation")
        void resolve_withUnknownCode_shouldReturnDefaultMessage() {
            assertThat(resolver.resolve(TestErrorCode.UNKNOWN_CODE, List.of(), Locale.ENGLISH))
                    .isEqualTo("An error occurred while processing the request.");
        }
    }

    @Nested
    @DisplayName("resolve with a validation error")
    class ResolveValidation {

        @Test
        @DisplayName("translates the constraint's message with its attributes")
        void resolve_withSizeConstraint_shouldUseAttributes() {
            // given: Spring passes the field and then the attributes in alphabetical order (max, min)
            var error = new DefaultMessageSourceResolvable(
                    new String[]{"Size.product.name", "Size"}, new Object[]{"name", 100, 3}, "size must be between 3 and 100");

            // when / then
            assertThat(resolver.resolve(error, SPANISH)).isEqualTo("debe tener entre 3 y 100 caracteres");
            assertThat(resolver.resolve(error, Locale.ENGLISH)).isEqualTo("must be between 3 and 100 characters long");
        }

        @Test
        @DisplayName("gives priority to a more specific message defined by the service")
        void resolve_withServiceSpecificMessage_shouldUseIt() {
            serviceMessages.addMessage("NotBlank.product.name", Locale.ENGLISH, "the product name is required");
            var error = new DefaultMessageSourceResolvable(
                    new String[]{"NotBlank.product.name", "NotBlank"}, new Object[]{"name"}, "must not be blank");

            assertThat(resolver.resolve(error, Locale.ENGLISH)).isEqualTo("the product name is required");
        }

        @Test
        @DisplayName("uses the validation's default message if there is no translation")
        void resolve_withUnknownConstraint_shouldUseDefaultMessage() {
            var error = new DefaultMessageSourceResolvable(
                    new String[]{"CustomConstraint"}, new Object[]{"field"}, "custom default message");
            assertThat(resolver.resolve(error, Locale.ENGLISH)).isEqualTo("custom default message");
        }
    }

    @Nested
    @DisplayName("message files")
    class MessageFiles {

        @ParameterizedTest
        @EnumSource(CommonErrorCode.class)
        @DisplayName("all the common codes have a message in English and Spanish")
        void allCommonCodes_shouldHaveEnglishAndSpanishMessages(CommonErrorCode errorCode) throws IOException {
            assertThat(load("i18n/common-messages_es.properties")).containsKey(errorCode.messageKey());
            assertThat(load("i18n/common-messages.properties")).containsKey(errorCode.messageKey());
        }

        private Properties load(String path) throws IOException {
            var properties = new Properties();
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                assertThat(in).as(path).isNotNull();
                properties.load(in);
            }
            return properties;
        }
    }
}
