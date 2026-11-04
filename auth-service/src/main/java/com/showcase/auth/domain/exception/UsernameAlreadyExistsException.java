package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * A user with that username already exists.
 */
public class UsernameAlreadyExistsException extends DuplicateResourceException {

    public UsernameAlreadyExistsException(Object... args) {
        super(AuthErrorCode.USERNAME_ALREADY_EXISTS, args);
    }
}
