package com.showcase.auth.infrastructure.persistence;

import com.showcase.auth.TestcontainersConfiguration;
import com.showcase.auth.infrastructure.config.PersistenceConfig;
import com.showcase.auth.infrastructure.persistence.mapper.PermissionEntityMapperImpl;
import com.showcase.auth.infrastructure.persistence.mapper.RoleEntityMapperImpl;
import com.showcase.auth.infrastructure.persistence.mapper.UserEntityMapperImpl;
import com.showcase.auth.infrastructure.persistence.repository.impl.PermissionRepositoryImpl;
import com.showcase.auth.infrastructure.persistence.repository.impl.RoleRepositoryImpl;
import com.showcase.auth.infrastructure.persistence.repository.impl.UserRepositoryImpl;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Base de los tests de persistencia: PostgreSQL real con Testcontainers, las migraciones de Flyway
 * aplicadas (el esquema se valida con ddl-auto=validate) y los adaptadores del servicio.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, AbstractPersistenceIT.PersistenceTestConfiguration.class})
public abstract class AbstractPersistenceIT {

    protected static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    @Import({PersistenceConfig.class,
            UserRepositoryImpl.class, RoleRepositoryImpl.class, PermissionRepositoryImpl.class,
            UserEntityMapperImpl.class, RoleEntityMapperImpl.class, PermissionEntityMapperImpl.class})
    static class PersistenceTestConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        GenericSpecificationBuilder genericSpecificationBuilder() {
            return new GenericSpecificationBuilder(true);
        }
    }
}
