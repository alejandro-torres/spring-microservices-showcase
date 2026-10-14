package com.showcase.product;

import org.springframework.boot.SpringApplication;

/**
 * Arranca product-service en local con un PostgreSQL en Docker y el perfil dev (catálogo de ejemplo):
 * {@code ./mvnw -pl product-service spring-boot:test-run -Dspring-boot.run.profiles=dev}.
 * Para llamar a la API hace falta un token de auth-service (que debe estar arrancado en el puerto 9000).
 */
public class TestProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(ProductServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
