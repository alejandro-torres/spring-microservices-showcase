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
    @DisplayName("resolve con un código de error")
    class ResolveErrorCode {

        @Test
        @DisplayName("devuelve el mensaje común en español con sus parámetros")
        void resolve_withSpanish_shouldReturnSpanishMessage() {
            var message = resolver.resolve(CommonErrorCode.TOO_MANY_FILTERS, List.of(10), SPANISH);
            assertThat(message).isEqualTo("Se permiten como máximo 10 filtros por petición.");
        }

        @Test
        @DisplayName("devuelve el mensaje común en inglés")
        void resolve_withEnglish_shouldReturnEnglishMessage() {
            var message = resolver.resolve(CommonErrorCode.INVALID_FILTER_VALUE, List.of("price", "abc"), Locale.US);
            assertThat(message).isEqualTo("Value 'abc' is not valid for the filter on field 'price'.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"fr", "de-DE", "pt"})
        @DisplayName("usa el español para los idiomas no soportados")
        void resolve_withUnsupportedLanguage_shouldFallBackToSpanish(String languageTag) {
            var message = resolver.resolve(CommonErrorCode.FORBIDDEN, List.of(), Locale.forLanguageTag(languageTag));
            assertThat(message).isEqualTo("No tienes permiso para realizar esta operación.");
        }

        @Test
        @DisplayName("da prioridad al mensaje del servicio sobre el común")
        void resolve_withServiceMessage_shouldUseIt() {
            serviceMessages.addMessage("error.FORBIDDEN", SPANISH, "Solo un administrador puede hacer esto.");
            assertThat(resolver.resolve(CommonErrorCode.FORBIDDEN, List.of(), SPANISH))
                    .isEqualTo("Solo un administrador puede hacer esto.");
        }

        @Test
        @DisplayName("resuelve los códigos propios de un servicio")
        void resolve_withServiceErrorCode_shouldUseServiceMessage() {
            serviceMessages.addMessage("error.PRODUCT_NOT_FOUND", Locale.ENGLISH, "Product with id {0} not found");
            assertThat(resolver.resolve(TestErrorCode.PRODUCT_NOT_FOUND, List.of(25), Locale.ENGLISH))
                    .isEqualTo("Product with id 25 not found");
        }

        @Test
        @DisplayName("no formatea los números de los parámetros (ids sin separador de miles)")
        void resolve_withLargeNumber_shouldNotFormatIt() {
            var message = resolver.resolve(CommonErrorCode.RESOURCE_NOT_FOUND, List.of("Product", 25000L), SPANISH);
            assertThat(message).isEqualTo("No se ha encontrado el recurso Product con id 25000.");
        }

        @Test
        @DisplayName("usa un mensaje genérico si el código no tiene traducción")
        void resolve_withUnknownCode_shouldReturnDefaultMessage() {
            assertThat(resolver.resolve(TestErrorCode.UNKNOWN_CODE, List.of(), SPANISH))
                    .isEqualTo("Se ha producido un error al procesar la petición.");
        }
    }

    @Nested
    @DisplayName("resolve con un error de validación")
    class ResolveValidation {

        @Test
        @DisplayName("traduce el mensaje de la restricción con sus atributos")
        void resolve_withSizeConstraint_shouldUseAttributes() {
            // given: Spring pasa el campo y después los atributos por orden alfabético (max, min)
            var error = new DefaultMessageSourceResolvable(
                    new String[]{"Size.product.name", "Size"}, new Object[]{"name", 100, 3}, "size must be between 3 and 100");

            // when / then
            assertThat(resolver.resolve(error, SPANISH)).isEqualTo("debe tener entre 3 y 100 caracteres");
            assertThat(resolver.resolve(error, Locale.ENGLISH)).isEqualTo("must be between 3 and 100 characters long");
        }

        @Test
        @DisplayName("da prioridad a un mensaje más específico definido por el servicio")
        void resolve_withServiceSpecificMessage_shouldUseIt() {
            serviceMessages.addMessage("NotBlank.product.name", SPANISH, "el nombre del producto es obligatorio");
            var error = new DefaultMessageSourceResolvable(
                    new String[]{"NotBlank.product.name", "NotBlank"}, new Object[]{"name"}, "must not be blank");

            assertThat(resolver.resolve(error, SPANISH)).isEqualTo("el nombre del producto es obligatorio");
        }

        @Test
        @DisplayName("usa el mensaje por defecto de la validación si no hay traducción")
        void resolve_withUnknownConstraint_shouldUseDefaultMessage() {
            var error = new DefaultMessageSourceResolvable(
                    new String[]{"CustomConstraint"}, new Object[]{"field"}, "custom default message");
            assertThat(resolver.resolve(error, SPANISH)).isEqualTo("custom default message");
        }
    }

    @Nested
    @DisplayName("ficheros de mensajes")
    class MessageFiles {

        @ParameterizedTest
        @EnumSource(CommonErrorCode.class)
        @DisplayName("todos los códigos comunes tienen mensaje en español y en inglés")
        void allCommonCodes_shouldHaveSpanishAndEnglishMessages(CommonErrorCode errorCode) throws IOException {
            assertThat(load("i18n/common-messages_es.properties")).containsKey(errorCode.messageKey());
            assertThat(load("i18n/common-messages_en.properties")).containsKey(errorCode.messageKey());
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
