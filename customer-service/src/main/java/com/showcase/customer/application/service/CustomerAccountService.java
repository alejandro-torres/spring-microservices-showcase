package com.showcase.customer.application.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactions to auth-service's user events (inbound port of the listeners). {@code eventId} makes each
 * operation idempotent: a repeated message has no effect.
 */
public interface CustomerAccountService {

    /** Creates the customer profile of a newly registered user (no addresses). */
    void createForUser(UUID eventId, Long userId, String email, String firstName, String lastName);

    /** Marks as inactive the customer of a deactivated or deleted user. */
    void deactivateForUser(UUID eventId, Long userId);

    /**
     * Records that the user's account has been locked. It does not change the customer: the lock is temporary and
     * lifts on its own, but it remains on record in the service's log.
     */
    void recordAccountLocked(UUID eventId, Long userId, Instant lockedUntil);
}
