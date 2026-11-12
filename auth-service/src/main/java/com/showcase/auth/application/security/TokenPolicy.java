package com.showcase.auth.application.security;

import java.time.Duration;

/**
 * @param accessTokenTtl lifetime of access tokens: a revocation does not need to last longer than that
 */
public record TokenPolicy(Duration accessTokenTtl) {
}
