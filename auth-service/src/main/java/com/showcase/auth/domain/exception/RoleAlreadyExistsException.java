package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * A role with that name already exists.
 */
public class RoleAlreadyExistsException extends DuplicateResourceException {

    public RoleAlreadyExistsException(Object... args) {
        super(AuthErrorCode.ROLE_ALREADY_EXISTS, args);
    }
}
