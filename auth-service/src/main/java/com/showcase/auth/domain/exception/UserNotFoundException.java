package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * No existe un usuario activo con el id indicado.
 */
public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(Object... args) {
        super(AuthErrorCode.USER_NOT_FOUND, args);
    }
}
