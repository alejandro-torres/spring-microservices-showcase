package com.showcase.gateway.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Decodifica y valida el JWT (firma con el JWKS de auth-service, caducidad) y después comprueba que no esté
 * revocado. Los validadores de Spring Security son síncronos; esta comprobación usa Redis reactivo, por eso va en
 * el decoder y no en un {@code OAuth2TokenValidator}.
 * <p>
 * Si Redis no responde, el token se rechaza: es preferible un 401 a dejar pasar un token que quizá esté revocado.
 */
@RequiredArgsConstructor
public class RevocationCheckingJwtDecoder implements ReactiveJwtDecoder {

    private static final Logger log = LoggerFactory.getLogger(RevocationCheckingJwtDecoder.class);
    private static final OAuth2Error REVOKED = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Token revoked", null);

    private final ReactiveJwtDecoder delegate;
    private final RevokedTokenChecker revokedTokenChecker;

    @Override
    public Mono<Jwt> decode(String token) {
        return delegate.decode(token).flatMap(jwt -> revokedTokenChecker.isRevoked(jwt)
                .onErrorMap(error -> {
                    log.error("No se pudo comprobar si el token está revocado; se rechaza", error);
                    return new BadJwtException("Revocation check unavailable", error);
                })
                .flatMap(revoked -> revoked
                        ? Mono.error(new JwtValidationException("Token revoked", List.of(REVOKED)))
                        : Mono.just(jwt)));
    }
}
