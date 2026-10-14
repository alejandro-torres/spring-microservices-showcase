package com.showcase.common.infrastructure.messaging.idempotency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Borra de {@code processed_event} los eventos procesados hace más de {@code retention}: RabbitMQ solo repite un
 * mensaje mientras no se confirma (minutos como mucho, con los reintentos), así que pasados unos días la tabla
 * solo crece. Con ShedLock solo la ejecuta una réplica.
 */
public class ProcessedEventCleanup {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanup.class);

    private final JdbcClient jdbcClient;
    private final Duration retention;
    private final Clock clock;

    public ProcessedEventCleanup(JdbcClient jdbcClient, Duration retention, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "${showcase.messaging.processed-event-cleanup-cron:0 30 3 * * *}")
    @SchedulerLock(name = "processedEventCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void deleteOldEvents() {
        int deleted = deleteProcessedBefore(Instant.now(clock).minus(retention));
        log.info("Limpieza de processed_event: {} eventos de hace más de {} borrados", deleted, retention);
    }

    /** Visible para los tests. */
    int deleteProcessedBefore(Instant limit) {
        return jdbcClient.sql("DELETE FROM processed_event WHERE processed_at < :limit")
                .param("limit", Timestamp.from(limit))
                .update();
    }
}
