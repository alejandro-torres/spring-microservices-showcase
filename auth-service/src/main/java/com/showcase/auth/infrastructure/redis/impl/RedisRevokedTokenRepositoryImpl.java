package com.showcase.auth.infrastructure.redis.impl;

import com.showcase.auth.domain.repository.RevokedTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tokens revocados en Redis con TTL: las claves desaparecen solas cuando el token habría caducado,
 * así que Redis nunca crece indefinidamente. Al estar fuera de la JVM, la revocación vale para todas
 * las réplicas a la vez.
 */
@Repository
@RequiredArgsConstructor
public class RedisRevokedTokenRepositoryImpl implements RevokedTokenRepository {

    static final String TOKEN_KEY = "auth:revoked:token:";
    static final String USER_KEY = "auth:revoked:user:";

    private final StringRedisTemplate redis;

    @Override
    public void revoke(String tokenId, Duration ttl) {
        redis.opsForValue().set(TOKEN_KEY + tokenId, "1", ttl);
    }

    @Override
    public boolean isRevoked(String tokenId) {
        return Boolean.TRUE.equals(redis.hasKey(TOKEN_KEY + tokenId));
    }

    @Override
    public void revokeIssuedBefore(String username, Instant instant, Duration ttl) {
        redis.opsForValue().set(USER_KEY + username, String.valueOf(instant.getEpochSecond()), ttl);
    }

    @Override
    public Optional<Instant> revokedBefore(String username) {
        String epochSeconds = redis.opsForValue().get(USER_KEY + username);
        return Optional.ofNullable(epochSeconds).map(Long::parseLong).map(Instant::ofEpochSecond);
    }
}
