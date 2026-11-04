package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * There is no active user with the given id.
 */
public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(Object... args) {
        super(AuthErrorCode.USER_NOT_FOUND, args);
    }
}
