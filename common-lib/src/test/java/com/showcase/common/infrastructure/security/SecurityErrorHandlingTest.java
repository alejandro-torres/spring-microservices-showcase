package com.showcase.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.showcase.common.infrastructure.exception.ErrorResponseFactory;
import com.showcase.common.infrastructure.i18n.ErrorMessageResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorHandlingTest {

    private final SecurityErrorResponseWriter writer = createWriter();

    private static SecurityErrorResponseWriter createWriter() {
        var responses = new ErrorResponseFactory(new ErrorMessageResolver(new StaticMessageSource()),
                Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC));
        var localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setDefaultLocale(Locale.ENGLISH);
        localeResolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("es")));
        return new SecurityErrorResponseWriter(responses, JsonMapper.builder().build(), localeResolver);
    }

    private static MockHttpServletRequest request(String uri) {
        var request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static Object json(MockHttpServletResponse response, String path) throws Exception {
        return JsonPath.read(response.getContentAsString(), path);
    }

    @Nested
    @DisplayName("RestAuthenticationEntryPoint")
    class EntryPoint {

        @Test
        @DisplayName("returns 401 UNAUTHORIZED in the common format if there are no credentials")
        void commence_withoutCredentials_shouldReturnUnauthorized() throws Exception {
            var response = new MockHttpServletResponse();

            new RestAuthenticationEntryPoint(writer).commence(request("/api/v1/users"), response,
                    new InsufficientAuthenticationException("Full authentication is required"));

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).startsWith("application/json");
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
            assertThat(json(response, "$.code")).isEqualTo("UNAUTHORIZED");
            assertThat(json(response, "$.message")).isEqualTo("Authentication is required to access this resource.");
            assertThat(json(response, "$.path")).isEqualTo("/api/v1/users");
            assertThat(json(response, "$.timestamp")).isEqualTo("2026-10-14T10:00:00Z");
        }

        @Test
        @DisplayName("returns 401 INVALID_TOKEN in the requested language if the token is invalid")
        void commence_withInvalidToken_shouldReturnInvalidToken() throws Exception {
            var request = request("/api/v1/users");
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "es");
            var response = new MockHttpServletResponse();

            new RestAuthenticationEntryPoint(writer).commence(request, response,
                    new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Jwt expired"));

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
            assertThat(json(response, "$.code")).isEqualTo("INVALID_TOKEN");
            assertThat(json(response, "$.message")).isEqualTo("El token de acceso no es válido, ha caducado o ha sido revocado.");
            assertThat(response.getContentAsString()).doesNotContain("Jwt expired");
        }
    }

    @Nested
    @DisplayName("RestAccessDeniedHandler")
    class AccessDenied {

        @Test
        @DisplayName("returns 403 FORBIDDEN in the common format")
        void handle_shouldReturnForbidden() throws Exception {
            var response = new MockHttpServletResponse();

            new RestAccessDeniedHandler(writer).handle(request("/api/v1/roles"), response,
                    new AccessDeniedException("Access Denied"));

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(json(response, "$.code")).isEqualTo("FORBIDDEN");
            assertThat(json(response, "$.message")).isEqualTo("You do not have permission to perform this operation.");
        }
    }
}
