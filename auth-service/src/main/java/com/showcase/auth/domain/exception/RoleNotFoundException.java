package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * There is no active role with the given id.
 */
public class RoleNotFoundException extends ResourceNotFoundException {

    public RoleNotFoundException(Object... args) {
        super(AuthErrorCode.ROLE_NOT_FOUND, args);
    }
}
