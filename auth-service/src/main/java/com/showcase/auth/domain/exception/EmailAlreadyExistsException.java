package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * A user with that email already exists.
 */
public class EmailAlreadyExistsException extends DuplicateResourceException {

    public EmailAlreadyExistsException(Object... args) {
        super(AuthErrorCode.EMAIL_ALREADY_EXISTS, args);
    }
}
