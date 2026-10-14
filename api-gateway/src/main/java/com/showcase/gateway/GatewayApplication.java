package com.showcase.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada único del sistema: valida el JWT y que no esté revocado, enruta a las réplicas registradas en
 * Eureka, limita la tasa de peticiones con Redis, corta con un circuit breaker los servicios caídos, devuelve el
 * {@code X-Trace-Id} y agrega la documentación de todos los servicios en un único Swagger UI.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
