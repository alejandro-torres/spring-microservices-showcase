package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * Ya existe un rol con ese nombre.
 */
public class RoleAlreadyExistsException extends DuplicateResourceException {

    public RoleAlreadyExistsException(Object... args) {
        super(AuthErrorCode.ROLE_ALREADY_EXISTS, args);
    }
}
