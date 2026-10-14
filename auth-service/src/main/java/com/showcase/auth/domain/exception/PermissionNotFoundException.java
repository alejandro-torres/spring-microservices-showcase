package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * No existe un permiso activo con el id indicado.
 */
public class PermissionNotFoundException extends ResourceNotFoundException {

    public PermissionNotFoundException(Object... args) {
        super(AuthErrorCode.PERMISSION_NOT_FOUND, args);
    }
}
