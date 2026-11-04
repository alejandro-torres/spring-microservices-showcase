package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * There is no active permission with the given id.
 */
public class PermissionNotFoundException extends ResourceNotFoundException {

    public PermissionNotFoundException(Object... args) {
        super(AuthErrorCode.PERMISSION_NOT_FOUND, args);
    }
}
