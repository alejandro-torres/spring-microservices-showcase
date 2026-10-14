package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * Ya existe un permiso con ese nombre.
 */
public class PermissionAlreadyExistsException extends DuplicateResourceException {

    public PermissionAlreadyExistsException(Object... args) {
        super(AuthErrorCode.PERMISSION_ALREADY_EXISTS, args);
    }
}
