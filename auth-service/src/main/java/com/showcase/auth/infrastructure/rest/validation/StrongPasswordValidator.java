package com.showcase.auth.infrastructure.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    static final int MIN_LENGTH = 8;
    /** BCrypt only uses the first 72 bytes: a longer password would give a false sense of security. */
    static final int MAX_LENGTH = 72;

    /** {@code @NotBlank} rejects a null value; only the strength is validated here. */
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }
        return password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH
                && password.chars().noneMatch(Character::isWhitespace)
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
    }
}
