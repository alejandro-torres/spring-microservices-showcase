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
 * Decodes and validates the JWT (signature with auth-service's JWKS, expiry) and then checks it is not
 * revoked. Spring Security's validators are synchronous; this check uses reactive Redis, which is why it lives in
 * the decoder and not in an {@code OAuth2TokenValidator}.
 * <p>
 * If Redis does not respond, the token is rejected: a 401 is preferable to letting through a token that may be revoked.
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
                    log.error("Could not check whether the token is revoked; rejecting it", error);
                    return new BadJwtException("Revocation check unavailable", error);
                })
                .flatMap(revoked -> revoked
                        ? Mono.error(new JwtValidationException("Token revoked", List.of(REVOKED)))
                        : Mono.just(jwt)));
    }
}
