package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * Ya existe un usuario con ese nombre de usuario.
 */
public class UsernameAlreadyExistsException extends DuplicateResourceException {

    public UsernameAlreadyExistsException(Object... args) {
        super(AuthErrorCode.USERNAME_ALREADY_EXISTS, args);
    }
}
