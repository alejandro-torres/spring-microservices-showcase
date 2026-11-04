package com.showcase.auth.infrastructure.rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Strong password: between 8 and 72 characters (BCrypt limit), no spaces, with at least one uppercase letter,
 * one lowercase letter, one number and one symbol. The translated message is in {@code messages*.properties}
 * under the {@code StrongPassword} key.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "must be a strong password";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
