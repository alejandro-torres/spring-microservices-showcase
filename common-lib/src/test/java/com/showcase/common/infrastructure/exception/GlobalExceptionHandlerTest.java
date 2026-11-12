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
        localeResolver.setDefaultLocale(Locale.ENGLISH);
        localeResolver.setSupportedLocales(List.of(Locale.ENGLISH, SPANISH));

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
    @DisplayName("domain exceptions")
    class DomainExceptions {

        @Test
        @DisplayName("returns 404 with the common format, the message in English and the traceId")
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
                    .andExpect(jsonPath("$.message").value("Product with id 25000 was not found."))
                    .andExpect(jsonPath("$.path").value("/test/not-found"))
                    .andExpect(jsonPath("$.traceId").value("a1b2c3d4e5"))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("translates the message to Spanish with Accept-Language es, without changing the code")
        void notFound_withSpanish_shouldTranslateMessageOnly() throws Exception {
            perform(get("/test/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "es-ES,es;q=0.9"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("No se ha encontrado el recurso Product con id 25000."));
        }

        @Test
        @DisplayName("uses English with an unsupported language and omits the traceId if there is no trace")
        void notFound_withUnsupportedLanguage_shouldUseEnglish() throws Exception {
            perform(get("/test/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                    .andExpect(jsonPath("$.message").value("Product with id 25000 was not found."))
                    .andExpect(jsonPath("$.traceId").doesNotExist());
        }

        @Test
        @DisplayName("returns 422 for a business rule and the generic message if there is no translation")
        void businessRule_shouldReturn422() throws Exception {
            perform(get("/test/business-rule"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                    .andExpect(jsonPath("$.message").value("An error occurred while processing the request."));
        }

        @Test
        @DisplayName("returns 503 when an external service fails")
        void externalService_shouldReturn503() throws Exception {
            perform(get("/test/external"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("returns 400 VALIDATION_ERROR with the translated list of invalid fields")
        void invalidBody_shouldReturnFieldErrors() throws Exception {
            perform(post("/test/body")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\",\"code\":\"ab\",\"quantity\":-1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("The request contains invalid data."))
                    .andExpect(jsonPath("$.errors", hasSize(3)))
                    .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "code", "quantity")))
                    .andExpect(jsonPath("$.errors[?(@.field=='code')].message").value("must be between 3 and 10 characters long"))
                    .andExpect(jsonPath("$.errors[?(@.field=='quantity')].message").value("must be greater than 0"));
        }

        @Test
        @DisplayName("translates the field errors to Spanish")
        void invalidBody_withSpanish_shouldTranslateFieldErrors() throws Exception {
            perform(post("/test/body")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "es")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"ok\",\"code\":\"ab\",\"quantity\":1}"))
                    .andExpect(jsonPath("$.errors[0].field").value("code"))
                    .andExpect(jsonPath("$.errors[0].message").value("debe tener entre 3 y 10 caracteres"));
        }

        @Test
        @DisplayName("returns 400 VALIDATION_ERROR if a parameter's validation fails")
        void invalidParam_shouldReturnValidationError() throws Exception {
            perform(get("/test/param").param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value("size"))
                    .andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));
        }

        @Test
        @DisplayName("returns 400 VALIDATION_ERROR for a ConstraintViolationException")
        void constraintViolation_shouldReturnValidationError() throws Exception {
            perform(get("/test/constraint-violation"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors", hasSize(3)))
                    .andExpect(jsonPath("$.errors[?(@.field=='name')].message").value("is required and cannot be blank"));
        }
    }

    @Nested
    @DisplayName("HTTP request errors")
    class RequestErrors {

        @Test
        @DisplayName("returns 400 MALFORMED_REQUEST with malformed JSON")
        void malformedJson_shouldReturn400() throws Exception {
            perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }

        @Test
        @DisplayName("returns 400 INVALID_PARAMETER if a parameter does not have the expected type")
        void typeMismatch_shouldReturn400() throws Exception {
            perform(get("/test/typed/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                    .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value: abc."));
        }

        @Test
        @DisplayName("returns 400 MISSING_PARAMETER if a required parameter is missing")
        void missingParameter_shouldReturn400() throws Exception {
            perform(get("/test/required"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        }

        @Test
        @DisplayName("returns 405 with the Allow header if the method is not allowed")
        void methodNotAllowed_shouldReturn405WithAllowHeader() throws Exception {
            perform(delete("/test/not-found"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("returns 415 if the content type is not supported")
        void unsupportedMediaType_shouldReturn415() throws Exception {
            perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("hello"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        }

        @Test
        @DisplayName("returns 404 ENDPOINT_NOT_FOUND for a path that does not exist")
        void unknownPath_shouldReturn404() throws Exception {
            perform(get("/does-not-exist"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("data and unexpected errors")
    class DataAndUnexpectedErrors {

        @Test
        @DisplayName("returns 409 CONCURRENT_MODIFICATION with an optimistic locking failure")
        void optimisticLock_shouldReturn409() throws Exception {
            perform(get("/test/optimistic-lock"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("returns 409 DATA_CONFLICT without showing the SQL message")
        void dataIntegrity_shouldReturn409WithoutSql() throws Exception {
            perform(get("/test/data-integrity"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                    .andExpect(jsonPath("$.message", not(containsString("products_name_key"))));
        }

        @Test
        @DisplayName("returns a generic 500 without Java details for an unexpected error")
        void unexpected_shouldReturnGeneric500() throws Exception {
            perform(get("/test/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("Internal server error."))
                    .andExpect(jsonPath("$.message", not(containsString("NullPointerException"))))
                    .andExpect(jsonPath("$.message", not(containsString("secret"))))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
    }
}
