package com.showcase.common.infrastructure.scheduling;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduled tasks with several replicas: each task annotated with {@code @SchedulerLock} is run by only one
 * replica at a time. The lock is a row in the {@code shedlock} table of the service's database (created by its
 * Flyway migration), which a replica takes with a conditional UPDATE and releases on finishing.
 * <ul>
 *   <li>{@code lockAtMostFor}: if the replica dies holding the lock, another recovers it after that time.</li>
 *   <li>{@code lockAtLeastFor}: prevents another replica repeating the task right after if the clocks disagree.</li>
 * </ul>
 * The database's time is used ({@code usingDbTime}) so as not to depend on the replicas' clocks being in sync.
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass({LockProvider.class, JdbcTemplateLockProvider.class, JdbcTemplate.class})
@ConditionalOnBean(DataSource.class)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "${showcase.scheduling.default-lock-at-most-for:30s}")
public class CommonSchedulingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
