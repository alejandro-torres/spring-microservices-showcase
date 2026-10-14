package com.showcase.auth.infrastructure.security.config;

import java.util.UUID;
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
 * Registra al arrancar los clientes OAuth2 de {@code showcase.security.clients} que aún no existen.
 * Los existentes no se tocan, para no pisar cambios hechos en la base de datos.
 */
@Component
public class RegisteredClientsInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegisteredClientsInitializer.class);

    private final RegisteredClientRepository repository;
    private final AuthSecurityProperties properties;
    private final PasswordEncoder passwordEncoder;

    public RegisteredClientsInitializer(RegisteredClientRepository repository, AuthSecurityProperties properties,
                                        PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (AuthSecurityProperties.Client client : properties.clients()) {
            if (repository.findByClientId(client.clientId()) != null) {
                continue;
            }
            boolean confidential = client.grantTypes().contains(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());
            if (confidential && client.isPublic()) {
                log.warn("El cliente {} usa client_credentials pero no tiene secreto configurado: no se registra",
                        client.clientId());
                continue;
            }
            repository.save(toRegisteredClient(client));
            log.info("Cliente OAuth2 registrado: {}", client.clientId());
        }
    }

    RegisteredClient toRegisteredClient(AuthSecurityProperties.Client client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(client.clientId())
                .clientName(client.clientName() == null ? client.clientId() : client.clientName())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(properties.accessTokenTtl())
                        .refreshTokenTimeToLive(properties.refreshTokenTtl())
                        // Rotación: cada uso del refresh token devuelve uno nuevo e invalida el anterior.
                        .reuseRefreshTokens(false)
                        .build());

        if (client.isPublic()) {
            // Aplicaciones de navegador: sin secreto y con PKCE obligatorio.
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
