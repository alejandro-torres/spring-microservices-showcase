package com.showcase.product.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transacciones programáticas de la reserva y devolución de stock. {@code StockServiceImpl} las abre DESPUÉS de
 * conseguir el lock distribuido (lock → transacción → commit → unlock), algo que {@code @Transactional} no permite
 * porque abriría la transacción antes que el lock.
 */
@Configuration(proxyBeanMethods = false)
public class StockTransactionConfig {

    public static final String RESERVE_STOCK = "reserveStockTransaction";
    public static final String RELEASE_STOCK = "releaseStockTransaction";

    /**
     * REPEATABLE_READ: en PostgreSQL, actualizar una fila que otra transacción confirmada ha modificado falla con
     * un error de serialización (y el mensaje se reintenta). Timeout de 5 s: nunca bloquea una conexión más.
     */
    @Bean(RESERVE_STOCK)
    public TransactionTemplate reserveStockTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        template.setTimeout(5);
        return template;
    }

    @Bean(RELEASE_STOCK)
    public TransactionTemplate releaseStockTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setTimeout(5);
        return template;
    }
}
