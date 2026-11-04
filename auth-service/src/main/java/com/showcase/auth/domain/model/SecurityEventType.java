package com.showcase.auth.domain.model;

/**
 * Security events recorded in the audit log.
 */
public enum SecurityEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    PASSWORD_CHANGED,
    ROLE_ASSIGNED,
    ROLE_REMOVED,
    TOKEN_REVOKED,
    USER_DISABLED
}
