package com.showcase.auth.infrastructure.security.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.showcase.auth.infrastructure.security.RevokedTokenValidator;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Claves RSA con las que se firman los JWT. Se publican en {@code /oauth2/jwks} para que el resto de
 * servicios validen los tokens con la clave pública, sin llamar a auth-service en cada petición.
 */
@Configuration(proxyBeanMethods = false)
public class JwkConfig {

    private static final Logger log = LoggerFactory.getLogger(JwkConfig.class);

    @Bean
    public JWKSource<SecurityContext> jwkSource(AuthSecurityProperties properties) {
        RSAKey rsaKey = properties.jwt() != null && properties.jwt().hasKeys()
                ? loadKey(properties.jwt())
                : generateKey();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /**
     * auth-service también valida los tokens de su propia API con la misma clave. A las comprobaciones por
     * defecto (firma, fechas) se añade la de tokens revocados.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, RevokedTokenValidator revokedTokenValidator) {
        JwtDecoder decoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        if (decoder instanceof NimbusJwtDecoder nimbusDecoder) {
            nimbusDecoder.setJwtValidator(
                    new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), revokedTokenValidator));
        }
        return decoder;
    }

    private static RSAKey loadKey(AuthSecurityProperties.Jwt jwt) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(decodePem(jwt.publicKey())));
            var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(jwt.privateKey())));
            String keyId = jwt.keyId() == null || jwt.keyId().isBlank() ? "showcase-key" : jwt.keyId();
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Las claves RSA de showcase.security.jwt no son válidas", e);
        }
    }

    // Con varias réplicas o tras un reinicio, una clave generada en memoria invalida los tokens: solo para desarrollo.
    private static RSAKey generateKey() {
        log.warn("No hay claves RSA configuradas (showcase.security.jwt): se genera un par en memoria. "
                + "Los tokens dejarán de ser válidos al reiniciar y no se comparten entre réplicas.");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo generar el par de claves RSA", e);
        }
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
