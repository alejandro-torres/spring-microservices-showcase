package com.showcase.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Registro de servicios. Cada réplica de cada servicio se registra con un instance-id único; el gateway y los
 * clientes Feign piden aquí la lista de réplicas vivas y reparten las peticiones entre ellas.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
