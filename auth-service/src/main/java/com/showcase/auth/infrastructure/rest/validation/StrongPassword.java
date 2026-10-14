package com.showcase.auth.infrastructure.rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contraseña segura: entre 8 y 72 caracteres (límite de BCrypt), sin espacios, con al menos una mayúscula,
 * una minúscula, un número y un símbolo. El mensaje traducido está en {@code messages*.properties}
 * con la clave {@code StrongPassword}.
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
