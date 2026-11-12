package com.showcase.auth.infrastructure.security.impl;

import com.showcase.auth.application.security.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Uses the same {@link PasswordEncoder} (BCrypt) as the login, so the hashes stored
 * when creating users are the ones Spring Security checks on login.
 */
@Component
@RequiredArgsConstructor
public class BCryptPasswordHasherImpl implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
