package com.showcase.product;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Docker containers for the integration tests (PostgreSQL, RabbitMQ and Redis) and to start the service
 * locally without installing anything ({@link TestProductServiceApplication}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }

    /**
     * Integration tests only. When starting the service locally, the shared RabbitMQ on
     * localhost:5672 is used ({@link TestProductServiceApplication}), so the services can talk to each other.
     */
    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "showcase.test.rabbitmq-container", havingValue = "true", matchIfMissing = true)
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:3-management");
    }

    /** Redis for Redisson's distributed locks. */
    @Bean
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }

    /**
     * product-service does not use Spring Data Redis, so {@code @ServiceConnection} would be of no use: Redisson reads
     * {@code spring.data.redis.host} and {@code port}, which point to the container here.
     */
    @Bean
    DynamicPropertyRegistrar redisProperties(GenericContainer<?> redisContainer) {
        return registry -> {
            registry.add("spring.data.redis.host", redisContainer::getHost);
            registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        };
    }
}
