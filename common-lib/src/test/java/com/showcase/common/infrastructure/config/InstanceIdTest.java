package com.showcase.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InstanceIdTest {

    @Nested
    @DisplayName("InstanceIdEnvironmentPostProcessor")
    class PostProcessor {

        private final InstanceIdEnvironmentPostProcessor postProcessor = new InstanceIdEnvironmentPostProcessor();

        @Test
        @DisplayName("define un instance-id con el nombre del servicio que no cambia al leerlo varias veces")
        void postProcess_shouldDefineStableInstanceId() {
            // given
            var environment = new MockEnvironment().withProperty("spring.application.name", "order-service");

            // when
            postProcessor.postProcessEnvironment(environment, new SpringApplication());

            // then
            String first = environment.getProperty("showcase.instance-id");
            assertThat(first).startsWith("order-service:").hasSizeGreaterThan("order-service:".length());
            assertThat(environment.getProperty("showcase.instance-id")).isEqualTo(first);
        }

        @Test
        @DisplayName("respeta un instance-id configurado explícitamente")
        void postProcess_withExplicitInstanceId_shouldKeepIt() {
            // given
            var environment = new MockEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("explicit",
                    Map.of("showcase.instance-id", "order-service:replica-1")));

            // when
            postProcessor.postProcessEnvironment(environment, new SpringApplication());

            // then
            assertThat(environment.getProperty("showcase.instance-id")).isEqualTo("order-service:replica-1");
        }
    }

    @Nested
    @DisplayName("InstanceIdResponseHeaderFilter")
    class Filter {

        @Test
        @DisplayName("añade la cabecera X-Instance-Id a la respuesta")
        void doFilter_shouldAddHeader() throws Exception {
            // given
            var response = new MockHttpServletResponse();

            // when
            new InstanceIdResponseHeaderFilter("product-service:abc123")
                    .doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

            // then
            assertThat(response.getHeader("X-Instance-Id")).isEqualTo("product-service:abc123");
        }
    }
}
