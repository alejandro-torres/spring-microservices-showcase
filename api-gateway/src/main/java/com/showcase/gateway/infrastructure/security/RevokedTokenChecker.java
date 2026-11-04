package com.showcase.gateway.infrastructure.security;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Checks in Redis the same list of revoked tokens that auth-service maintains (logout, password change,
 * deactivated user...). A token is revoked if:
 * <ul>
 *   <li>its {@code jti} is in {@code auth:revoked:token:{jti}}, or</li>
 *   <li>it was issued no later than the instant stored in {@code auth:revoked:user:{username}} (revocation of all
 *       the user's tokens).</li>
 * </ul>
 * The keys are those of auth-service's {@code RedisRevokedTokenRepositoryImpl}: if they change there, they change here.
 */
@Component
@RequiredArgsConstructor
public class RevokedTokenChecker {

    static final String TOKEN_KEY = "auth:revoked:token:";
    static final String USER_KEY = "auth:revoked:user:";

    private final ReactiveStringRedisTemplate redis;

    public Mono<Boolean> isRevoked(Jwt jwt) {
        Mono<Boolean> tokenRevoked = jwt.getId() == null ? Mono.just(false) : redis.hasKey(TOKEN_KEY + jwt.getId());
        return tokenRevoked.flatMap(revoked -> revoked ? Mono.just(true) : revokedForUser(jwt));
    }

    private Mono<Boolean> revokedForUser(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        if (issuedAt == null || jwt.getSubject() == null) {
            return Mono.just(false);
        }
        return redis.opsForValue().get(USER_KEY + jwt.getSubject())
                .map(epochSeconds -> !issuedAt.isAfter(Instant.ofEpochSecond(Long.parseLong(epochSeconds))))
                .defaultIfEmpty(false);
    }
}
