package com.showcase.auth.application.security;

/**
 * Outbound port to hash passwords. The application does not know which algorithm is used
 * (the infrastructure implements it with BCrypt).
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
