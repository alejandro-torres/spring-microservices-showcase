package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * Ya existe un usuario con ese email.
 */
public class EmailAlreadyExistsException extends DuplicateResourceException {

    public EmailAlreadyExistsException(Object... args) {
        super(AuthErrorCode.EMAIL_ALREADY_EXISTS, args);
    }
}
