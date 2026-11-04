package com.showcase.order;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;

/**
 * Starts order-service locally with PostgreSQL in Docker and the dev profile (sample orders):
 * {@code ./mvnw -pl order-service spring-boot:test-run -Dspring-boot.run.profiles=dev}.
 * Calling the API requires a token from auth-service (which must be running on port 9000).
 */
public class TestOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(OrderServiceApplication::main)
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
