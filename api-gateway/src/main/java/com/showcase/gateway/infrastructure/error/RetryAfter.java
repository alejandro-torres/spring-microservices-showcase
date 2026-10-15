package com.showcase.gateway.infrastructure.error;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpHeaders;

/**
 * Segundos que tiene que esperar el cliente tras un 429. Con el token bucket de Redis, una petición necesita
 * {@code requestedTokens} fichas y el cubo recupera {@code replenishRate} por segundo, así que la siguiente
 * petición cabe como mucho en {@code ceil(requestedTokens / replenishRate)} segundos.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RetryAfter {

    static final long DEFAULT_SECONDS = 1;

    public static long seconds(HttpHeaders rateLimitHeaders) {
        try {
            long requested = Long.parseLong(rateLimitHeaders.getFirst(RedisRateLimiter.REQUESTED_TOKENS_HEADER));
            long replenishRate = Long.parseLong(rateLimitHeaders.getFirst(RedisRateLimiter.REPLENISH_RATE_HEADER));
            if (requested <= 0 || replenishRate <= 0) {
                return DEFAULT_SECONDS;
            }
            return Math.max(1, (requested + replenishRate - 1) / replenishRate);
        } catch (NumberFormatException e) {
            return DEFAULT_SECONDS;
        }
    }
}
