package com.showcase.auth.infrastructure.security.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del servidor de autorización ({@code showcase.security.*}).
 *
 * @param issuer          URL pública del emisor de los tokens (claim {@code iss})
 * @param accessTokenTtl  duración de los tokens de acceso
 * @param refreshTokenTtl duración de los refresh tokens (rotan en cada uso)
 * @param jwt             claves RSA en PEM; si faltan se genera un par en memoria (solo para desarrollo)
 * @param clients         clientes OAuth2 que se registran al arrancar si no existen
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
     * @param maxFailedAttempts intentos fallidos seguidos que bloquean la cuenta
     * @param lockDuration      duración del bloqueo
     */
    public record Login(@DefaultValue("5") int maxFailedAttempts, @DefaultValue("15m") Duration lockDuration) {
    }

    public record Jwt(String keyId, String privateKey, String publicKey) {

        public boolean hasKeys() {
            return privateKey != null && !privateKey.isBlank() && publicKey != null && !publicKey.isBlank();
        }
    }

    /**
     * @param secret      vacío para clientes públicos (aplicaciones de navegador con PKCE)
     * @param grantTypes  authorization_code, refresh_token y/o client_credentials
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
