package com.showcase.auth.infrastructure.rest.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Secret-123", "Aa1!aaaa", "Password#2026", "P@ssw0rdVeryLong_withAll1"})
    @DisplayName("accepts passwords with uppercase, lowercase, digits and symbols")
    void isValid_strongPasswords_shouldBeAccepted(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Aa1!aaa",        // 7 caracteres
            "secret-123",    // no uppercase
            "SECRET-123",    // no lowercase
            "Secret-abc",    // no digits
            "Secreta123",     // no symbols
            "Secret 123!",   // with spaces
            ""
    })
    @DisplayName("rejects weak passwords")
    void isValid_weakPasswords_shouldBeRejected(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    @DisplayName("rejects more than 72 characters (BCrypt would ignore the rest) and leaves nulls to @NotBlank")
    void isValid_limits() {
        assertThat(validator.isValid("Aa1!" + "a".repeat(69), null)).isFalse();
        assertThat(validator.isValid("Aa1!" + "a".repeat(68), null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
