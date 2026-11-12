package com.showcase.common.infrastructure.messaging.idempotency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Deletes from {@code processed_event} the events processed more than {@code retention} ago: RabbitMQ only repeats a
 * message while it is unacknowledged (minutes at most, with retries), so after a few days the table only grows.
 * With ShedLock only one replica runs it.
 */
@RequiredArgsConstructor
public class ProcessedEventCleanup {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanup.class);

    private final JdbcClient jdbcClient;
    private final Duration retention;
    private final Clock clock;

    @Scheduled(cron = "${showcase.messaging.processed-event-cleanup-cron:0 30 3 * * *}")
    @SchedulerLock(name = "processedEventCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void deleteOldEvents() {
        int deleted = deleteProcessedBefore(Instant.now(clock).minus(retention));
        log.info("processed_event cleanup: {} events older than {} deleted", deleted, retention);
    }

    /** Visible for tests. */
    int deleteProcessedBefore(Instant limit) {
        return jdbcClient.sql("DELETE FROM processed_event WHERE processed_at < :limit")
                .param("limit", Timestamp.from(limit))
                .update();
    }
}
