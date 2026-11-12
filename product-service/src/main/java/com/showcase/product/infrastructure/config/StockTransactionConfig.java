package com.showcase.product.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Programmatic transactions for stock reservation and return. {@code StockServiceImpl} opens them AFTER
 * acquiring the distributed lock (lock → transaction → commit → unlock), which {@code @Transactional} does not allow
 * because it would open the transaction before the lock.
 */
@Configuration(proxyBeanMethods = false)
public class StockTransactionConfig {

    public static final String RESERVE_STOCK = "reserveStockTransaction";
    public static final String RELEASE_STOCK = "releaseStockTransaction";

    /**
     * REPEATABLE_READ: in PostgreSQL, updating a row that another committed transaction has modified fails with
     * a serialization error (and the message is retried). 5 s timeout: it never holds a connection longer.
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
