package com.showcase.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single entry point of the system: validates the JWT and that it is not revoked, routes to the replicas registered
 * in Eureka, rate limits requests with Redis, cuts off downed services with a circuit breaker, returns the
 * {@code X-Trace-Id} and aggregates the documentation of all services in a single Swagger UI.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
