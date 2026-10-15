package com.showcase.common.infrastructure.metrics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ejecuta una acción solo si la transacción en curso se confirma (o enseguida, si no hay transacción). Las
 * métricas de negocio la usan para no contar, por ejemplo, un pedido "creado" cuya transacción acaba en rollback.
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
