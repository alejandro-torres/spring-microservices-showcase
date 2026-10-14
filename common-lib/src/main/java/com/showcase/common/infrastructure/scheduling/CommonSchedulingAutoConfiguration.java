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
 * Tareas programadas con varias réplicas: cada tarea anotada con {@code @SchedulerLock} la ejecuta una sola
 * réplica a la vez. El lock es una fila de la tabla {@code shedlock} de la base de datos del servicio (la crea su
 * migración Flyway), que una réplica coge con un UPDATE condicional y suelta al terminar.
 * <ul>
 *   <li>{@code lockAtMostFor}: si la réplica muere con el lock cogido, otra lo recupera pasado ese tiempo.</li>
 *   <li>{@code lockAtLeastFor}: evita que otra réplica repita la tarea justo después si los relojes no coinciden.</li>
 * </ul>
 * Se usa la hora de la base de datos ({@code usingDbTime}) para no depender de que los relojes de las réplicas
 * estén sincronizados.
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
