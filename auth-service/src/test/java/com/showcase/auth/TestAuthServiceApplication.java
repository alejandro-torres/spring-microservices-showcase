package com.showcase.auth;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;

/**
 * Arranca auth-service en local con un PostgreSQL en Docker y el perfil dev (datos de ejemplo):
 * {@code ./mvnw -pl auth-service spring-boot:test-run -Dspring-boot.run.profiles=dev}
 */
public class TestAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(AuthServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(withSharedRabbitMq(args));
    }

    // Sin contenedor de RabbitMQ propio: se conecta al compartido (RABBITMQ_HOST, por defecto localhost:5672).
    private static String[] withSharedRabbitMq(String[] args) {
        return Stream.concat(Stream.of("--showcase.test.rabbitmq-container=false"), Arrays.stream(args))
                .toArray(String[]::new);
    }
}
