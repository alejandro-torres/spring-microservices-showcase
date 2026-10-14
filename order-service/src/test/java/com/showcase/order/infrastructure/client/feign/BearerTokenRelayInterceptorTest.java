package com.showcase.order.infrastructure.client.feign;

import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestTemplate;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class BearerTokenRelayInterceptorTest {

    private final BearerTokenRelayInterceptor interceptor = new BearerTokenRelayInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("apply")
    class Apply {

        @Test
        @DisplayName("reenvía el JWT del usuario en la cabecera Authorization")
        void apply_withJwt_shouldAddBearerHeader() {
            var jwt = Jwt.withTokenValue("token-del-usuario").header("alg", "RS256").subject("ana")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
            var template = new RequestTemplate();

            interceptor.apply(template);

            assertThat(template.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer token-del-usuario");
        }

        @Test
        @DisplayName("no añade nada si no hay un JWT en el contexto")
        void apply_withoutJwt_shouldNotAddHeader() {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("ana", null, java.util.List.of()));
            var template = new RequestTemplate();

            interceptor.apply(template);

            assertThat(template.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
        }
    }
}
