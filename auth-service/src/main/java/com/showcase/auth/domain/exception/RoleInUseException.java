package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ConflictException;

/**
 * The role is assigned to active users and cannot be deleted.
 */
public class RoleInUseException extends ConflictException {

    public RoleInUseException(Object... args) {
        super(AuthErrorCode.ROLE_IN_USE, args);
    }
}
