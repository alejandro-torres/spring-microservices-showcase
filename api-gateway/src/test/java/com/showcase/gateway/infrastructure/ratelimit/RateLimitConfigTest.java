package com.showcase.gateway.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class RateLimitConfigTest {

    @Nested
    @DisplayName("clientIp")
    class ClientIp {

        @Test
        @DisplayName("detrás de un proxy usa la primera IP de X-Forwarded-For (la del cliente)")
        void clientIp_withForwardedFor_shouldUseFirstAddress() {
            var request = MockServerHttpRequest.get("/login")
                    .header("X-Forwarded-For", "203.0.113.7, 10.0.0.2").build();

            assertThat(RateLimitConfig.clientIp(request)).isEqualTo("203.0.113.7");
        }

        @Test
        @DisplayName("sin proxy usa la dirección remota de la conexión")
        void clientIp_withoutForwardedFor_shouldUseRemoteAddress() {
            var request = MockServerHttpRequest.get("/login")
                    .remoteAddress(new InetSocketAddress("198.51.100.4", 50000)).build();

            assertThat(RateLimitConfig.clientIp(request)).isEqualTo("198.51.100.4");
        }
    }
}
