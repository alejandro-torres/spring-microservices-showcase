package com.showcase.gateway.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Firma JWT como lo haría auth-service, con una clave RSA generada para el test y publicada en un JWKS. */
public final class TestTokens {

    private final RSAKey key;

    public TestTokens() {
        try {
            key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public String jwksJson() {
        return new JWKSet(key.toPublicJWK()).toString();
    }

    public String token(String username) {
        return token(username, UUID.randomUUID().toString(), Instant.now());
    }

    public String token(String username, String jti, Instant issuedAt) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(username)
                .jwtID(jti)
                .issuer("http://localhost:9000")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(900)))
                .claim("roles", List.of("USER"))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
