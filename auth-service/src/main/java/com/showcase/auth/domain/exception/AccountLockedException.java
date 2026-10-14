package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.DomainException;
import java.time.Instant;

/**
 * La cuenta está bloqueada por demasiados intentos de login fallidos (423).
 */
public class AccountLockedException extends DomainException {

    public AccountLockedException(String username, Instant lockedUntil) {
        super(AuthErrorCode.ACCOUNT_LOCKED, username, lockedUntil);
    }
}
