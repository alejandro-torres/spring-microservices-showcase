package com.showcase.auth.infrastructure.security;

import com.showcase.auth.application.service.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Rejects revoked JWTs even if their signature and date are valid. It is added to the default
 * validators (signature, expiry) of the API's JwtDecoder.
 *
 * <p>If Redis does not respond, the token is rejected (fail closed): a 401 is preferable to accepting a token
 * that could be revoked.
 */
@Component
@RequiredArgsConstructor
public class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenValidator.class);
    private static final OAuth2Error REVOKED = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Token revoked", null);
    private static final OAuth2Error UNAVAILABLE =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Revocation check unavailable", null);

    private final TokenRevocationService tokenRevocationService;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            return tokenRevocationService.isRevoked(jwt.getId(), jwt.getSubject(), jwt.getIssuedAt())
                    ? OAuth2TokenValidatorResult.failure(REVOKED)
                    : OAuth2TokenValidatorResult.success();
        } catch (RuntimeException e) {
            log.error("Could not check whether the token is revoked; rejecting it", e);
            return OAuth2TokenValidatorResult.failure(UNAVAILABLE);
        }
    }
}
