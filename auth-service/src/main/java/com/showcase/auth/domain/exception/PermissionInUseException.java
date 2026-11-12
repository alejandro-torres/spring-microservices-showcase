package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ConflictException;

/**
 * The permission is included in active roles and cannot be deleted.
 */
public class PermissionInUseException extends ConflictException {

    public PermissionInUseException(Object... args) {
        super(AuthErrorCode.PERMISSION_IN_USE, args);
    }
}
