package com.showcase.common.infrastructure.config;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Calcula UNA vez al arrancar el identificador de esta réplica, {@code showcase.instance-id}, con el formato
 * {@code <servicio>:<id>}. Es el mismo valor que se registra en Eureka y el que devuelve la cabecera
 * {@code X-Instance-Id}, para ver qué réplica atendió cada petición.
 * <p>
 * Hace falta porque {@code ${random.uuid}} da un valor distinto cada vez que se resuelve: usado en dos sitios,
 * Eureka y la cabecera mostrarían ids diferentes. El id es el {@code HOSTNAME} (en Docker, el id del contenedor)
 * o, si no existe, un UUID corto.
 */
public class InstanceIdEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY = "showcase.instance-id";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.containsProperty(PROPERTY)) {
            return;
        }
        String hostname = System.getenv("HOSTNAME");
        String suffix = hostname != null && !hostname.isBlank() ? hostname
                : UUID.randomUUID().toString().substring(0, 8);
        // El nombre del servicio se resuelve después, cuando ya se ha leído application.yml.
        environment.getPropertySources().addLast(new MapPropertySource("showcaseInstanceId",
                Map.of(PROPERTY, "${spring.application.name:app}:" + suffix)));
    }
}
