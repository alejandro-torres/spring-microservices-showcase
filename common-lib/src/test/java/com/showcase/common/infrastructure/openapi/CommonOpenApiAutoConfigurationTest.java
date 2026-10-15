package com.showcase.common.infrastructure.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommonOpenApiAutoConfigurationTest {

    private final CommonOpenApiAutoConfiguration configuration = new CommonOpenApiAutoConfiguration();

    @Nested
    @DisplayName("openApi")
    class OpenApi {

        @Test
        @DisplayName("declara el Bearer JWT y el login OAuth2 como alternativas")
        void openApi_shouldDeclareBearerAndOAuth2AsAlternatives() {
            // when
            OpenAPI openApi = configuration.openApi("product-service", "");

            // then
            assertThat(openApi.getComponents().getSecuritySchemes())
                    .containsOnlyKeys(CommonOpenApiAutoConfiguration.SECURITY_SCHEME,
                            CommonOpenApiAutoConfiguration.OAUTH2_SCHEME);
            assertThat(openApi.getSecurity()).extracting(SecurityRequirement::keySet)
                    .containsExactly(Set.of(CommonOpenApiAutoConfiguration.SECURITY_SCHEME),
                            Set.of(CommonOpenApiAutoConfiguration.OAUTH2_SCHEME));
        }

        @Test
        @DisplayName("declara un servidor relativo para que «Try it out» no use el prefijo /servicio del gateway")
        void openApi_shouldDeclareRelativeServer() {
            // when
            OpenAPI openApi = configuration.openApi("auth-service", "");

            // then
            assertThat(openApi.getServers()).singleElement()
                    .satisfies(server -> assertThat(server.getUrl()).isEqualTo("/"));
        }
    }

    @Nested
    @DisplayName("oauth2Scheme")
    class OAuth2Scheme {

        @Test
        @DisplayName("sin URL del servidor usa rutas relativas, que el Swagger UI del gateway resuelve contra sí mismo")
        void oauth2Scheme_withoutUrl_shouldUseRelativePaths() {
            // when
            SecurityScheme scheme = CommonOpenApiAutoConfiguration.oauth2Scheme("");

            // then
            assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.OAUTH2);
            var flow = scheme.getFlows().getAuthorizationCode();
            assertThat(flow.getAuthorizationUrl()).isEqualTo("/oauth2/authorize");
            assertThat(flow.getTokenUrl()).isEqualTo("/oauth2/token");
            assertThat(flow.getScopes()).containsOnlyKeys("openid", "profile");
        }

        @Test
        @DisplayName("con URL del servidor (con o sin barra final) construye las URLs absolutas")
        void oauth2Scheme_withUrl_shouldUseAbsoluteUrls() {
            // when
            var flow = CommonOpenApiAutoConfiguration.oauth2Scheme("http://localhost:9000/").getFlows()
                    .getAuthorizationCode();

            // then
            assertThat(flow.getAuthorizationUrl()).isEqualTo("http://localhost:9000/oauth2/authorize");
            assertThat(flow.getTokenUrl()).isEqualTo("http://localhost:9000/oauth2/token");
        }
    }
}
