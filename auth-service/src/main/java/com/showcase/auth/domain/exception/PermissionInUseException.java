package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ConflictException;

/**
 * El permiso está incluido en roles activos y no se puede borrar.
 */
public class PermissionInUseException extends ConflictException {

    public PermissionInUseException(Object... args) {
        super(AuthErrorCode.PERMISSION_IN_USE, args);
    }
}
