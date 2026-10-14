package com.showcase.order;

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
                .run(args);
    }
}
