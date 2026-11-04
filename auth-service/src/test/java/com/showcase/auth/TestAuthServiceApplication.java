package com.showcase.auth;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;

/**
 * Starts auth-service locally with PostgreSQL in Docker and the dev profile (sample data):
 * {@code ./mvnw -pl auth-service spring-boot:test-run -Dspring-boot.run.profiles=dev}
 */
public class TestAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(AuthServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(localArguments(args));
    }

    // Locally, the shared RabbitMQ (localhost:5672) and discovery-service's Eureka (localhost:8761) are used,
    // so the started services can find and talk to each other.
    private static String[] localArguments(String[] args) {
        return Stream.concat(Stream.of("--showcase.test.rabbitmq-container=false", "--eureka.client.enabled=true"),
                        Arrays.stream(args))
                .toArray(String[]::new);
    }
}
