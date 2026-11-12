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
 * Real OAuth2 flows against the full application: password login in PostgreSQL,
 * authorization_code with PKCE, client_credentials and use of the JWT in the API.
 */
@SpringBootTest(properties = "INTERNAL_CLIENT_SECRET=internal-test-secret")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OAuth2FlowsIT {

    private static final String REDIRECT_URI = "http://127.0.0.1:3000/callback";
    private static final String PASSWORD = "Secret-Test-123";

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

    /** Walks through authorization_code + PKCE like a browser and returns the JSON response of /oauth2/token. */
    private String authorizationCodeFlow(String username, String password) throws Exception {
        String verifier = randomVerifier();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var session = new MockHttpSession();

        // 1. The browser requests authorization without a session: the server stores the request and redirects to the
        // login.
        //    The parameters go in the query string: the authorization server only reads them from there.
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

        // 2. The user submits the login form and is sent back to the stored authorization request.
        String afterLogin = mockMvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        // 3. Once authenticated, the server redirects to the application with the code (302: a normal part of the
        // flow).
        String callback = mockMvc.perform(get(URI.create(afterLogin)).session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Map<String, List<String>> params = UriComponentsBuilder.fromUriString(callback).build().getQueryParams();
        assertThat(callback).startsWith(REDIRECT_URI);
        assertThat(params.get("state")).containsExactly("estado-123");
        assertThat(params).as("redirect with the code: %s", callback).containsKey("code");

        // 4. The application redeems the code by proving it holds the code_verifier (PKCE).
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
    @DisplayName("authorization_code with PKCE")
    class AuthorizationCode {

        @Test
        @DisplayName("an ADMIN gets a JWT with their roles and permissions and can use the API")
        void admin_shouldGetTokenWithRolesAndUseApi() throws Exception {
            createUser("flow.admin", "ADMIN");

            String response = authorizationCodeFlow("flow.admin", PASSWORD);
            String accessToken = JsonPath.read(response, "$.access_token");
            // OAuth 2.1 best practice: public clients (browser) do not get a refresh token.
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
        @DisplayName("a registered user (USER role) can access /me but not the user listing")
        void registeredUser_shouldOnlyAccessOwnData() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                            {"username":"new.customer","email":"new@example.com","password":"%s",
                             "firstName":"New","lastName":"Customer"}
                            """.formatted(PASSWORD)))
                    .andExpect(status().isCreated());

            String accessToken = JsonPath.read(authorizationCodeFlow("new.customer", PASSWORD), "$.access_token");

            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles[0]").value("USER"));
            mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("a wrong password does not complete the login")
        void wrongPassword_shouldFailLogin() throws Exception {
            createUser("flow.wrong", "USER");

            mockMvc.perform(post("/login").with(csrf()).param("username", "flow.wrong").param("password", "wrong"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).contains("/login?error"));
        }
    }

    @Nested
    @DisplayName("client_credentials and token validation")
    class ClientCredentialsAndValidation {

        @Test
        @DisplayName("a service gets a token with the internal scope and no user roles")
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
        @DisplayName("rejects a wrong client secret")
        void clientCredentials_withWrongSecret_shouldReturn401() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                            .with(httpBasic("internal-service", "wrong"))
                            .param("grant_type", "client_credentials"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("the API rejects requests with no token or a tampered token, in the common format")
        void api_withoutOrWithInvalidToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJSUzI1NiJ9.e30.firma"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("publishes the public keys (JWKS) and the OpenID configuration without authentication")
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
