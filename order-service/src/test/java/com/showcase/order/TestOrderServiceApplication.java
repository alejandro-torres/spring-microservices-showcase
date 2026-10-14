package com.showcase.order;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;

/**
 * Arranca order-service en local con un PostgreSQL en Docker y el perfil dev (pedidos de ejemplo):
 * {@code ./mvnw -pl order-service spring-boot:test-run -Dspring-boot.run.profiles=dev}.
 * Para llamar a la API hace falta un token de auth-service (que debe estar arrancado en el puerto 9000).
 */
public class TestOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(OrderServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(localArguments(args));
    }

    // En local se usan el RabbitMQ compartido (localhost:5672) y el Eureka de discovery-service (localhost:8761),
    // para que los servicios arrancados se encuentren y se comuniquen entre sí.
    private static String[] localArguments(String[] args) {
        return Stream.concat(Stream.of("--showcase.test.rabbitmq-container=false", "--eureka.client.enabled=true"),
                        Arrays.stream(args))
                .toArray(String[]::new);
    }
}
