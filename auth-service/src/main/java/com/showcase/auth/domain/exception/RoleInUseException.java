package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ConflictException;

/**
 * El rol está asignado a usuarios activos y no se puede borrar.
 */
public class RoleInUseException extends ConflictException {

    public RoleInUseException(Object... args) {
        super(AuthErrorCode.ROLE_IN_USE, args);
    }
}
