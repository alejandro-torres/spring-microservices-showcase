package com.showcase.auth.infrastructure.security.impl;

import com.showcase.auth.application.security.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Usa el mismo {@link PasswordEncoder} (BCrypt) que el login, para que los hashes que se guardan
 * al crear usuarios sean los que Spring Security comprueba al iniciar sesión.
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
