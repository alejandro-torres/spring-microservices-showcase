package com.showcase.customer;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;

/**
 * Arranca customer-service en local con un PostgreSQL en Docker y el perfil dev (clientes de ejemplo):
 * {@code ./mvnw -pl customer-service spring-boot:test-run -Dspring-boot.run.profiles=dev}.
 * Para llamar a la API hace falta un token de auth-service (que debe estar arrancado en el puerto 9000).
 */
public class TestCustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(CustomerServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(withSharedRabbitMq(args));
    }

    // Sin contenedor de RabbitMQ propio: se conecta al compartido (RABBITMQ_HOST, por defecto localhost:5672).
    private static String[] withSharedRabbitMq(String[] args) {
        return Stream.concat(Stream.of("--showcase.test.rabbitmq-container=false"), Arrays.stream(args))
                .toArray(String[]::new);
    }
}
