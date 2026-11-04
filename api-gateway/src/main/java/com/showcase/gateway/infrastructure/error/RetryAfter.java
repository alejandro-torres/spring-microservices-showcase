package com.showcase.gateway.infrastructure.error;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpHeaders;

/**
 * Seconds the client must wait after a 429. With Redis's token bucket, a request needs
 * {@code requestedTokens} tokens and the bucket recovers {@code replenishRate} per second, so the next
 * request fits in at most {@code ceil(requestedTokens / replenishRate)} seconds.
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
