package com.showcase.auth.infrastructure.security.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Authorization server configuration ({@code showcase.security.*}).
 *
 * @param issuer          public URL of the token issuer ({@code iss} claim)
 * @param accessTokenTtl  lifetime of access tokens
 * @param refreshTokenTtl lifetime of refresh tokens (they rotate on each use)
 * @param jwt             RSA keys in PEM; if missing, a pair is generated in memory (development only)
 * @param clients         OAuth2 clients registered at startup if they do not exist
 */
@ConfigurationProperties("showcase.security")
public record AuthSecurityProperties(
        @DefaultValue("http://localhost:9000") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("8h") Duration refreshTokenTtl,
        Jwt jwt,
        @DefaultValue List<Client> clients,
        @DefaultValue Login login) {

    /**
     * @param maxFailedAttempts consecutive failed attempts that lock the account
     * @param lockDuration      duration of the lock
     */
    public record Login(@DefaultValue("5") int maxFailedAttempts, @DefaultValue("15m") Duration lockDuration) {
    }

    public record Jwt(String keyId, String privateKey, String publicKey) {

        public boolean hasKeys() {
            return privateKey != null && !privateKey.isBlank() && publicKey != null && !publicKey.isBlank();
        }
    }

    /**
     * @param secret      empty for public clients (browser applications with PKCE)
     * @param grantTypes  authorization_code, refresh_token and/or client_credentials
     */
    public record Client(
            String clientId,
            String clientName,
            String secret,
            Set<String> grantTypes,
            @DefaultValue Set<String> redirectUris,
            @DefaultValue Set<String> scopes) {

        public boolean isPublic() {
            return secret == null || secret.isBlank();
        }
    }
}
