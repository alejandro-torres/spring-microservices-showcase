package com.showcase.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServiceIT {

    @LocalServerPort
    private int port;

    @Nested
    @DisplayName("Eureka Server")
    class EurekaServer {

        @Test
        @DisplayName("arranca, está sano y expone el registro de aplicaciones (vacío)")
        void registry_shouldBeAvailable() {
            // given
            RestClient client = RestClient.create("http://localhost:" + port);

            // when
            String health = client.get().uri("/actuator/health").retrieve().body(String.class);
            String apps = client.get().uri("/eureka/apps").accept(MediaType.APPLICATION_JSON).retrieve()
                    .body(String.class);

            // then
            assertThat(health).contains("\"status\":\"UP\"");
            assertThat(apps).contains("\"applications\"");
        }
    }
}
