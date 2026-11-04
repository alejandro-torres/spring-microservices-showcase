package com.showcase.common.infrastructure.metrics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs an action only if the current transaction commits (or immediately, if there is no transaction). Business
 * metrics use it so as not to count, for example, a "created" order whose transaction ends in a rollback.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AfterCommit {

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
