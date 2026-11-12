package com.showcase.common.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

    private final AtomicInteger runs = new AtomicInteger();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Nested
    @DisplayName("run")
    class Run {

        @Test
        @DisplayName("without a transaction it runs the action immediately")
        void run_withoutTransaction_shouldRunNow() {
            // when
            AfterCommit.run(runs::incrementAndGet);

            // then
            assertThat(runs).hasValue(1);
        }

        @Test
        @DisplayName("within a transaction it waits for the commit")
        void run_withinTransaction_shouldWaitForCommit() {
            // given
            TransactionSynchronizationManager.initSynchronization();

            // when
            AfterCommit.run(runs::incrementAndGet);

            // then
            assertThat(runs).hasValue(0);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertThat(runs).hasValue(1);
        }

        @Test
        @DisplayName("if the transaction rolls back it does not run")
        void run_withRollback_shouldNotRun() {
            // given
            TransactionSynchronizationManager.initSynchronization();

            // when
            AfterCommit.run(runs::incrementAndGet);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            // then
            assertThat(runs).hasValue(0);
        }
    }
}
