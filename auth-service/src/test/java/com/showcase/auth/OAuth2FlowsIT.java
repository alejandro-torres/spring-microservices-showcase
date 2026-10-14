package com.showcase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.repository.RoleRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Flujos OAuth2 reales contra la aplicación completa: login con contraseña en PostgreSQL,
 * authorization_code con PKCE, client_credentials y uso del JWT en la API.
 */
@SpringBootTest(properties = "INTERNAL_CLIENT_SECRET=internal-test-secret")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OAuth2FlowsIT {

    private static final String REDIRECT_URI = "http://127.0.0.1:3000/callback";
    private static final String PASSWORD = "Secreta-Test-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    private void createUser(String username, String... roles) {
        Set<Long> roleIds = new java.util.HashSet<>();
        for (String role : roles) {
            roleIds.add(roleRepository.findByName(role).orElseThrow().getId());
        }
        userService.create(username, username + "@example.com", PASSWORD, roleIds);
    }

    /** Recorre authorization_code + PKCE como un navegador y devuelve la respuesta JSON de /oauth2/token. */
    private String authorizationCodeFlow(String username, String password) throws Exception {
        String verifier = randomVerifier();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var session = new MockHttpSession();

        // 1. El navegador pide autorización sin sesión: el servidor guarda la petición y redirige al login.
        //    Los parámetros van en la query string: el servidor de autorización solo los lee de ahí.
        URI authorizeUri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "web-client")
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("scope", "openid profile")
                .queryParam("state", "estado-123")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .encode().build().toUri();
        mockMvc.perform(get(authorizeUri).session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());

        // 2. El usuario envía el formulario de login; se le devuelve a la petición de autorización guardada.
        String afterLogin = mockMvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        // 3. Ya autenticado, el servidor redirige a la aplicación con el código (302: parte normal del flujo).
        String callback = mockMvc.perform(get(URI.create(afterLogin)).session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Map<String, List<String>> params = UriComponentsBuilder.fromUriString(callback).build().getQueryParams();
        assertThat(callback).startsWith(REDIRECT_URI);
        assertThat(params.get("state")).containsExactly("estado-123");
        assertThat(params).as("redirección con el código: %s", callback).containsKey("code");

        // 4. La aplicación canjea el código demostrando que tiene el code_verifier (PKCE).
        return mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", params.get("code").getFirst())
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", "web-client")
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String randomVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> claims(String jwt) {
        String payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonMapper.builder().build().readValue(payload, Map.class);
    }

    @Nested
    @DisplayName("authorization_code con PKCE")
    class AuthorizationCode {

        @Test
        @DisplayName("un ADMIN obtiene un JWT con sus roles y permisos y puede usar la API")
        void admin_shouldGetTokenWithRolesAndUseApi() throws Exception {
            createUser("flow.admin", "ADMIN");

            String response = authorizationCodeFlow("flow.admin", PASSWORD);
            String accessToken = JsonPath.read(response, "$.access_token");
            // Buena práctica de OAuth 2.1: los clientes públicos (navegador) no reciben refresh token.
            assertThat(response).doesNotContain("refresh_token");

            Map<String, Object> claims = claims(accessToken);
            assertThat(claims.get("sub")).isEqualTo("flow.admin");
            assertThat((List<String>) claims.get("roles")).containsExactly("ADMIN");
            assertThat((List<String>) claims.get("permissions")).contains("user:read", "user:write", "order:write");
            assertThat(((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue())
                    .isEqualTo(15 * 60);

            mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(jsonPath("$.username").value("flow.admin"));
        }

        @Test
        @DisplayName("un usuario registrado (rol USER) accede a /me pero no al listado de usuarios")
        void registeredUser_shouldOnlyAccessOwnData() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                            {"username":"nuevo.cliente","email":"nuevo@example.com","password":"%s"}
                            """.formatted(PASSWORD)))
                    .andExpect(status().isCreated());

            String accessToken = JsonPath.read(authorizationCodeFlow("nuevo.cliente", PASSWORD), "$.access_token");

            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles[0]").value("USER"));
            mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("una contraseña incorrecta no completa el login")
        void wrongPassword_shouldFailLogin() throws Exception {
            createUser("flow.wrong", "USER");

            mockMvc.perform(post("/login").with(csrf()).param("username", "flow.wrong").param("password", "incorrecta"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).contains("/login?error"));
        }
    }

    @Nested
    @DisplayName("client_credentials y validación de tokens")
    class ClientCredentialsAndValidation {

        @Test
        @DisplayName("un servicio obtiene un token con scope internal y sin roles de usuario")
        void clientCredentials_shouldIssueServiceToken() throws Exception {
            String response = mockMvc.perform(post("/oauth2/token")
                            .with(httpBasic("internal-service", "internal-test-secret"))
                            .param("grant_type", "client_credentials")
                            .param("scope", "internal"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            Map<String, Object> claims = claims(JsonPath.read(response, "$.access_token"));
            assertThat(claims.get("sub")).isEqualTo("internal-service");
            assertThat(claims).doesNotContainKeys("roles", "permissions");
        }

        @Test
        @DisplayName("rechaza un secreto de cliente incorrecto")
        void clientCredentials_withWrongSecret_shouldReturn401() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                            .with(httpBasic("internal-service", "incorrecto"))
                            .param("grant_type", "client_credentials"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("la API rechaza peticiones sin token o con un token manipulado, en el formato común")
        void api_withoutOrWithInvalidToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJSUzI1NiJ9.e30.firma"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("publica las claves públicas (JWKS) y la configuración OpenID sin autenticación")
        void publicEndpoints_shouldBeAvailable() throws Exception {
            mockMvc.perform(get("/oauth2/jwks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                    .andExpect(jsonPath("$.keys[0].d").doesNotExist());
            mockMvc.perform(get("/.well-known/openid-configuration"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.issuer").value("http://localhost:9000"));
        }
    }
}
