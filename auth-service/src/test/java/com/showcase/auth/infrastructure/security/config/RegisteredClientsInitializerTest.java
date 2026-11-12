package com.showcase.auth.infrastructure.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

class RegisteredClientsInitializerTest {

    private static final AuthSecurityProperties.Client WEB = new AuthSecurityProperties.Client("web-client", "Web",
            null, Set.of("authorization_code", "refresh_token"), Set.of("http://127.0.0.1:3000/callback"),
            Set.of("openid"));
    private static final AuthSecurityProperties.Client INTERNAL = new AuthSecurityProperties.Client(
            "internal-service", null, "secret", Set.of("client_credentials"), Set.of(), Set.of("internal"));
    private static final AuthSecurityProperties.Client INTERNAL_WITHOUT_SECRET = new AuthSecurityProperties.Client(
            "internal-service", null, "", Set.of("client_credentials"), Set.of(), Set.of("internal"));

    private final RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private RegisteredClientsInitializer initializer(AuthSecurityProperties.Client... clients) {
        var properties = new AuthSecurityProperties("http://localhost:9000", Duration.ofMinutes(15),
                Duration.ofHours(8), null, List.of(clients), new AuthSecurityProperties.Login(5, Duration.ofMinutes(15)));
        return new RegisteredClientsInitializer(repository, properties, passwordEncoder);
    }

    @Test
    @DisplayName("registers a public client with mandatory PKCE, no secret and refresh token rotation")
    void run_publicClient_shouldRequirePkce() {
        initializer(WEB).run(null);

        var captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(repository).save(captor.capture());
        var client = captor.getValue();
        assertThat(client.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getClientSecret()).isNull();
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(15));
        assertThat(client.getAuthorizationGrantTypes())
                .contains(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
    }

    @Test
    @DisplayName("stores a confidential client's secret hashed with BCrypt")
    void run_confidentialClient_shouldHashSecret() {
        initializer(INTERNAL).run(null);

        var captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getClientSecret()).isNotEqualTo("secret");
        assertThat(passwordEncoder.matches("secret", captor.getValue().getClientSecret())).isTrue();
    }

    @Test
    @DisplayName("does not register clients that already exist or client_credentials clients without a secret")
    void run_existingOrWithoutSecret_shouldSkip() {
        given(repository.findByClientId("web-client")).willReturn(
                initializer(WEB).toRegisteredClient(WEB));

        initializer(WEB, INTERNAL_WITHOUT_SECRET).run(null);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("synchronizes the redirect URIs of an existing client and keeps its id and configuration")
    void run_existingWithOtherRedirectUris_shouldUpdateOnlyRedirectUris() {
        // given: the client registered when it only had the frontend URI
        RegisteredClient existing = initializer(WEB).toRegisteredClient(WEB);
        given(repository.findByClientId("web-client")).willReturn(existing);
        var withSwagger = new AuthSecurityProperties.Client("web-client", "Web", null,
                Set.of("authorization_code", "refresh_token"),
                Set.of("http://127.0.0.1:3000/callback", "http://localhost:8080/swagger-ui/oauth2-redirect.html"),
                Set.of("openid", "profile"));

        // when
        initializer(withSwagger).run(null);

        // then
        var captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(repository).save(captor.capture());
        var updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getRedirectUris()).isEqualTo(withSwagger.redirectUris());
        assertThat(updated.getScopes()).isEqualTo(existing.getScopes());
        assertThat(updated.getClientSettings().isRequireProofKey()).isTrue();
    }
}
