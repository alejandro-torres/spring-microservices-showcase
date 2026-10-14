package com.showcase.auth.application.security;

/**
 * Puerto de salida para cifrar contraseñas. La aplicación no sabe qué algoritmo se usa
 * (la infraestructura lo implementa con BCrypt).
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
