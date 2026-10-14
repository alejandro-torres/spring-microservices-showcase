package com.showcase.common.infrastructure.exception;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.common.infrastructure.i18n.ErrorMessageResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:15:30Z");
    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var responses = new ErrorResponseFactory(
                new ErrorMessageResolver(new StaticMessageSource()), Clock.fixed(NOW, ZoneOffset.UTC));
        var localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setDefaultLocale(SPANISH);
        localeResolver.setSupportedLocales(List.of(SPANISH, Locale.ENGLISH));

        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
                .setControllerAdvice(
                        new GlobalExceptionHandler(responses, new ErrorCodeHttpStatusMapper()),
                        new DataAccessExceptionHandler(responses),
                        new ConstraintViolationExceptionHandler(responses))
                .setLocaleResolver(localeResolver)
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private ResultActions perform(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    @Nested
    @DisplayName("excepciones de dominio")
    class DomainExceptions {

        @Test
        @DisplayName("devuelve 404 con el formato común, el mensaje en español y el traceId")
        void notFound_shouldReturn404WithCommonFormat() throws Exception {
            // given
            MDC.put("traceId", "a1b2c3d4e5");

            // when / then
            perform(get("/test/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").value("2026-10-14T10:15:30Z"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("No se ha encontrado el recurso Product con id 25000."))
                    .andExpect(jsonPath("$.path").value("/test/not-found"))
                    .andExpect(jsonPath("$.traceId").value("a1b2c3d4e5"))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("traduce el mensaje al inglés con Accept-Language en, sin cambiar el código")
        void notFound_withEnglish_shouldTranslateMessageOnly() throws Exception {
            perform(get("/test/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Product with id 25000 was not found."));
        }

        @Test
        @DisplayName("usa el español con un idioma no soportado y omite el traceId si no hay traza")
        void notFound_withUnsupportedLanguage_shouldUseSpanish() throws Exception {
            perform(get("/test/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                    .andExpect(jsonPath("$.message").value("No se ha encontrado el recurso Product con id 25000."))
                    .andExpect(jsonPath("$.traceId").doesNotExist());
        }

        @Test
        @DisplayName("devuelve 422 para una regla de negocio y el mensaje genérico si no hay traducción")
        void businessRule_shouldReturn422() throws Exception {
            perform(get("/test/business-rule"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                    .andExpect(jsonPath("$.message").value("Se ha producido un error al procesar la petición."));
        }

        @Test
        @DisplayName("devuelve 503 cuando falla un servicio externo")
        void externalService_shouldReturn503() throws Exception {
            perform(get("/test/external"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        }
    }

    @Nested
    @DisplayName("validación")
    class Validation {

        @Test
        @DisplayName("devuelve 400 VALIDATION_ERROR con la lista de campos inválidos traducida")
        void invalidBody_shouldReturnFieldErrors() throws Exception {
            perform(post("/test/body")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\",\"code\":\"ab\",\"quantity\":-1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("La petición contiene datos no válidos."))
                    .andExpect(jsonPath("$.errors", hasSize(3)))
                    .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "code", "quantity")))
                    .andExpect(jsonPath("$.errors[?(@.field=='code')].message").value("debe tener entre 3 y 10 caracteres"))
                    .andExpect(jsonPath("$.errors[?(@.field=='quantity')].message").value("debe ser mayor que 0"));
        }

        @Test
        @DisplayName("traduce los errores de campo al inglés")
        void invalidBody_withEnglish_shouldTranslateFieldErrors() throws Exception {
            perform(post("/test/body")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"ok\",\"code\":\"ab\",\"quantity\":1}"))
                    .andExpect(jsonPath("$.errors[0].field").value("code"))
                    .andExpect(jsonPath("$.errors[0].message").value("must be between 3 and 10 characters long"));
        }

        @Test
        @DisplayName("devuelve 400 VALIDATION_ERROR si falla la validación de un parámetro")
        void invalidParam_shouldReturnValidationError() throws Exception {
            perform(get("/test/param").param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("size"))
                    .andExpect(jsonPath("$.errors[0].message").value("debe ser menor o igual que 100"));
        }

        @Test
        @DisplayName("devuelve 400 VALIDATION_ERROR para una ConstraintViolationException")
        void constraintViolation_shouldReturnValidationError() throws Exception {
            perform(get("/test/constraint-violation"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors", hasSize(3)))
                    .andExpect(jsonPath("$.errors[?(@.field=='name')].message").value("es obligatorio y no puede estar vacío"));
        }
    }

    @Nested
    @DisplayName("errores de la petición HTTP")
    class RequestErrors {

        @Test
        @DisplayName("devuelve 400 MALFORMED_REQUEST con un JSON mal formado")
        void malformedJson_shouldReturn400() throws Exception {
            perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }

        @Test
        @DisplayName("devuelve 400 INVALID_PARAMETER si un parámetro no tiene el tipo esperado")
        void typeMismatch_shouldReturn400() throws Exception {
            perform(get("/test/typed/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.message").value("El parámetro 'id' tiene un valor no válido: abc."));
        }

        @Test
        @DisplayName("devuelve 400 MISSING_PARAMETER si falta un parámetro obligatorio")
        void missingParameter_shouldReturn400() throws Exception {
            perform(get("/test/required"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        }

        @Test
        @DisplayName("devuelve 405 con la cabecera Allow si el método no está permitido")
        void methodNotAllowed_shouldReturn405WithAllowHeader() throws Exception {
            perform(delete("/test/not-found"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("devuelve 415 si el tipo de contenido no está soportado")
        void unsupportedMediaType_shouldReturn415() throws Exception {
            perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("hola"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        }

        @Test
        @DisplayName("devuelve 404 ENDPOINT_NOT_FOUND para una ruta que no existe")
        void unknownPath_shouldReturn404() throws Exception {
            perform(get("/no-existe"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("errores de datos e inesperados")
    class DataAndUnexpectedErrors {

        @Test
        @DisplayName("devuelve 409 CONCURRENT_MODIFICATION con un fallo de bloqueo optimista")
        void optimisticLock_shouldReturn409() throws Exception {
            perform(get("/test/optimistic-lock"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("devuelve 409 DATA_CONFLICT sin mostrar el mensaje SQL")
        void dataIntegrity_shouldReturn409WithoutSql() throws Exception {
            perform(get("/test/data-integrity"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                    .andExpect(jsonPath("$.message", not(containsString("products_name_key"))));
        }

        @Test
        @DisplayName("devuelve 500 genérico sin detalles de Java para un error inesperado")
        void unexpected_shouldReturnGeneric500() throws Exception {
            perform(get("/test/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Error interno del servidor."))
                    .andExpect(jsonPath("$.message", not(containsString("NullPointerException"))))
                    .andExpect(jsonPath("$.message", not(containsString("secreto"))))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
    }
}
