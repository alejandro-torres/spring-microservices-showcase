package com.showcase.auth.infrastructure.rest.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Secreta-123", "Aa1!aaaa", "Contraseña#2026", "P@ssw0rdMuyLarga_conTodo1"})
    @DisplayName("acepta contraseñas con mayúsculas, minúsculas, números y símbolos")
    void isValid_strongPasswords_shouldBeAccepted(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Aa1!aaa",        // 7 caracteres
            "secreta-123",    // sin mayúsculas
            "SECRETA-123",    // sin minúsculas
            "Secreta-abc",    // sin números
            "Secreta123",     // sin símbolos
            "Secreta 123!",   // con espacios
            ""
    })
    @DisplayName("rechaza contraseñas débiles")
    void isValid_weakPasswords_shouldBeRejected(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    @DisplayName("rechaza más de 72 caracteres (BCrypt ignoraría el resto) y deja los nulos a @NotBlank")
    void isValid_limits() {
        assertThat(validator.isValid("Aa1!" + "a".repeat(69), null)).isFalse();
        assertThat(validator.isValid("Aa1!" + "a".repeat(68), null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
