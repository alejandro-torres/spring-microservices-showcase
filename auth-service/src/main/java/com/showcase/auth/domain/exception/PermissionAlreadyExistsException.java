package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * A permission with that name already exists.
 */
public class PermissionAlreadyExistsException extends DuplicateResourceException {

    public PermissionAlreadyExistsException(Object... args) {
        super(AuthErrorCode.PERMISSION_ALREADY_EXISTS, args);
    }
}
