package com.showcase.common.infrastructure.config;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Computes ONCE at startup the identifier of this replica, {@code showcase.instance-id}, in the format
 * {@code <service>:<id>}. It is the same value registered in Eureka and returned by the
 * {@code X-Instance-Id} header, to see which replica served each request.
 * <p>
 * It is needed because {@code ${random.uuid}} gives a different value each time it is resolved: used in two places,
 * Eureka and the header would show different ids. The id is the {@code HOSTNAME} (in Docker, the container id)
 * or, if missing, a short UUID.
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
        // The service name is resolved later, once application.yml has been read.
        environment.getPropertySources().addLast(new MapPropertySource("showcaseInstanceId",
                Map.of(PROPERTY, "${spring.application.name:app}:" + suffix)));
    }
}
