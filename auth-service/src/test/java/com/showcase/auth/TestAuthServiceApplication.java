package com.showcase.auth;

import org.springframework.boot.SpringApplication;

/**
 * Arranca auth-service en local con un PostgreSQL en Docker y el perfil dev (datos de ejemplo):
 * {@code ./mvnw -pl auth-service spring-boot:test-run -Dspring-boot.run.profiles=dev}
 */
public class TestAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(AuthServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
