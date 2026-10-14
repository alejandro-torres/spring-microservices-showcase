package com.showcase.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Contenedores de Docker para los tests de integración (PostgreSQL y RabbitMQ) y para arrancar el servicio en
 * local sin instalar nada ({@link TestOrderServiceApplication}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }

    /**
     * Solo en los tests de integración. Al arrancar el servicio en local se usa el RabbitMQ compartido de
     * localhost:5672 ({@link TestOrderServiceApplication}), para que los servicios se comuniquen entre sí.
     */
    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "showcase.test.rabbitmq-container", havingValue = "true", matchIfMissing = true)
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:3-management");
    }
}
