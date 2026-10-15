package com.showcase.gateway.infrastructure.security;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Consulta en Redis la misma lista de tokens revocados que mantiene auth-service (logout, cambio de contraseña,
 * usuario desactivado...). Un token está revocado si:
 * <ul>
 *   <li>su {@code jti} está en {@code auth:revoked:token:{jti}}, o</li>
 *   <li>se emitió no después del instante guardado en {@code auth:revoked:user:{username}} (revocación de todos
 *       los tokens del usuario).</li>
 * </ul>
 * Las claves son las de {@code RedisRevokedTokenRepositoryImpl} de auth-service: si cambian allí, cambian aquí.
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
