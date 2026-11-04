package com.showcase.auth.application.service;

/**
 * Tracking of login attempts to lock accounts under brute-force attack.
 */
public interface LoginAttemptService {

    /** Successful login: resets the counter and audits it. */
    void onSuccess(String username);

    /**
     * Failed login due to a wrong password: increments the counter and, on reaching the maximum, locks the account.
     *
     * @throws com.showcase.auth.domain.exception.AccountLockedException if this attempt locks the account
     */
    void onFailure(String username);
}
