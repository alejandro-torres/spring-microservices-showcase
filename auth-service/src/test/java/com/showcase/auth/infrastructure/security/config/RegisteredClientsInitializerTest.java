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
            "internal-service", null, "secreto", Set.of("client_credentials"), Set.of(), Set.of("internal"));
    private static final AuthSecurityProperties.Client INTERNAL_WITHOUT_SECRET = new AuthSecurityProperties.Client(
            "internal-service", null, "", Set.of("client_credentials"), Set.of(), Set.of("internal"));

    private final RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private RegisteredClientsInitializer initializer(AuthSecurityProperties.Client... clients) {
        var properties = new AuthSecurityProperties("http://localhost:9000", Duration.ofMinutes(15),
                Duration.ofHours(8), null, List.of(clients));
        return new RegisteredClientsInitializer(repository, properties, passwordEncoder);
    }

    @Test
    @DisplayName("registra un cliente público con PKCE obligatorio, sin secreto y con rotación de refresh tokens")
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
    @DisplayName("guarda el secreto de un cliente confidencial cifrado con BCrypt")
    void run_confidentialClient_shouldHashSecret() {
        initializer(INTERNAL).run(null);

        var captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getClientSecret()).isNotEqualTo("secreto");
        assertThat(passwordEncoder.matches("secreto", captor.getValue().getClientSecret())).isTrue();
    }

    @Test
    @DisplayName("no registra clientes que ya existen ni clientes client_credentials sin secreto")
    void run_existingOrWithoutSecret_shouldSkip() {
        given(repository.findByClientId("web-client")).willReturn(
                initializer(WEB).toRegisteredClient(WEB));

        initializer(WEB, INTERNAL_WITHOUT_SECRET).run(null);

        verify(repository, never()).save(any());
    }
}
