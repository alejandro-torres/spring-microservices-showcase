package com.showcase.auth.application.security;

import java.time.Duration;

/**
 * @param accessTokenTtl vida de los tokens de acceso: una revocación no necesita durar más que eso
 */
public record TokenPolicy(Duration accessTokenTtl) {
}
