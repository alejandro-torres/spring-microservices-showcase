package com.showcase.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Contenedores de Docker para los tests de integración (PostgreSQL, Redis y RabbitMQ) y para arrancar el
 * servicio en local sin instalar nada ({@link TestAuthServiceApplication}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }

    /**
     * Solo en los tests de integración. Al arrancar el servicio en local se usa el RabbitMQ compartido de
     * localhost:5672 ({@link TestAuthServiceApplication}), para que los servicios se comuniquen entre sí.
     */
    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "showcase.test.rabbitmq-container", havingValue = "true", matchIfMissing = true)
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:3-management");
    }
}
