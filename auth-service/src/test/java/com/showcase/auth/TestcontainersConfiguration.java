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
 * Docker containers for the integration tests (PostgreSQL, Redis and RabbitMQ) and to start the service
 * locally without installing anything ({@link TestAuthServiceApplication}).
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
     * Integration tests only. When starting the service locally, the shared RabbitMQ on
     * localhost:5672 is used ({@link TestAuthServiceApplication}), so the services can talk to each other.
     */
    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "showcase.test.rabbitmq-container", havingValue = "true", matchIfMissing = true)
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:3-management");
    }
}
