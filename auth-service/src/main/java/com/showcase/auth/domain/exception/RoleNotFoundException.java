package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * No existe un rol activo con el id indicado.
 */
public class RoleNotFoundException extends ResourceNotFoundException {

    public RoleNotFoundException(Object... args) {
        super(AuthErrorCode.ROLE_NOT_FOUND, args);
    }
}
