package com.showcase.auth.infrastructure.security.config;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/**
 * Registers at startup the OAuth2 clients in {@code showcase.security.clients} that do not exist yet.
 * For existing ones, only the redirect URIs are synchronized with the configuration (it decides where an
 * authorization code can be sent); the rest is left alone, so as not to overwrite changes made in the database.
 */
@Component
@RequiredArgsConstructor
public class RegisteredClientsInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegisteredClientsInitializer.class);

    private final RegisteredClientRepository repository;
    private final AuthSecurityProperties properties;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        for (AuthSecurityProperties.Client client : properties.clients()) {
            RegisteredClient existing = repository.findByClientId(client.clientId());
            if (existing != null) {
                syncRedirectUris(existing, client);
                continue;
            }
            boolean confidential = client.grantTypes().contains(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());
            if (confidential && client.isPublic()) {
                log.warn("Client {} uses client_credentials but has no secret configured: it is not registered",
                        client.clientId());
                continue;
            }
            repository.save(toRegisteredClient(client));
            log.info("OAuth2 client registered: {}", client.clientId());
        }
    }

    private void syncRedirectUris(RegisteredClient existing, AuthSecurityProperties.Client client) {
        if (existing.getRedirectUris().equals(client.redirectUris())) {
            return;
        }
        repository.save(RegisteredClient.from(existing)
                .redirectUris(uris -> {
                    uris.clear();
                    uris.addAll(client.redirectUris());
                })
                .build());
        log.info("Redirect URIs of OAuth2 client {} updated: {}", client.clientId(), client.redirectUris());
    }

    RegisteredClient toRegisteredClient(AuthSecurityProperties.Client client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(client.clientId())
                .clientName(client.clientName() == null ? client.clientId() : client.clientName())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(properties.accessTokenTtl())
                        .refreshTokenTimeToLive(properties.refreshTokenTtl())
                        // Rotation: each use of the refresh token returns a new one and invalidates the previous one.
                        .reuseRefreshTokens(false)
                        .build());

        if (client.isPublic()) {
            // Browser applications: no secret and mandatory PKCE.
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build());
        } else {
            builder.clientSecret(passwordEncoder.encode(client.secret()))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build());
        }
        client.grantTypes().forEach(grant -> builder.authorizationGrantType(new AuthorizationGrantType(grant)));
        client.redirectUris().forEach(builder::redirectUri);
        client.scopes().forEach(builder::scope);
        return builder.build();
    }
}
