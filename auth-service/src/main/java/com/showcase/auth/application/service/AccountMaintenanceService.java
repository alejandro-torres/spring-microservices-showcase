package com.showcase.auth.application.service;

/**
 * Periodic maintenance of accounts and tokens (inbound port of the scheduled tasks).
 */
public interface AccountMaintenanceService {

    /**
     * Unlocks the accounts whose failed-attempts lock has already expired and records it in the audit log.
     *
     * @return how many were unlocked
     */
    int unlockExpiredAccounts();

    /**
     * Deletes the OAuth2 authorizations in which everything has expired (codes, access and refresh tokens).
     *
     * @return how many were deleted
     */
    int deleteExpiredAuthorizations();
}
