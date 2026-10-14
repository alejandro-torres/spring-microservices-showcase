package com.showcase.auth.infrastructure.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    static final int MIN_LENGTH = 8;
    /** BCrypt solo usa los primeros 72 bytes: una contraseña más larga daría una falsa sensación de seguridad. */
    static final int MAX_LENGTH = 72;

    /** Un valor nulo lo rechaza {@code @NotBlank}; aquí solo se valida la fortaleza. */
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
