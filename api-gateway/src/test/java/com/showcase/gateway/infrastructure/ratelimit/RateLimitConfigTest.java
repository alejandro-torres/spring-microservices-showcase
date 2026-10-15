package com.showcase.gateway.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class RateLimitConfigTest {

    private static MockServerWebExchange exchange(String forwardedFor) {
        var request = MockServerHttpRequest.post("/login")
                .remoteAddress(new InetSocketAddress("198.51.100.4", 50000));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return MockServerWebExchange.from(request);
    }

    @Nested
    @DisplayName("clientIp")
    class ClientIp {

        @Test
        @DisplayName("sin proxies de confianza usa la IP de la conexión e ignora X-Forwarded-For (se puede falsificar)")
        void clientIp_withoutTrustedProxies_shouldIgnoreForwardedFor() {
            var config = new RateLimitConfig(0);

            assertThat(config.clientIp(exchange("203.0.113.7"))).isEqualTo("198.51.100.4");
        }

        @Test
        @DisplayName("con un proxy de confianza usa la IP que añadió ese proxy, no las que inventa el cliente")
        void clientIp_withOneTrustedProxy_shouldUseLastForwardedAddress() {
            var config = new RateLimitConfig(1);

            // El cliente envía "1.2.3.4" falsificado; el proxy añade la IP real 203.0.113.7 al final.
            assertThat(config.clientIp(exchange("1.2.3.4, 203.0.113.7"))).isEqualTo("203.0.113.7");
        }

        @Test
        @DisplayName("con un proxy de confianza pero sin cabecera, usa la IP de la conexión")
        void clientIp_withTrustedProxyButNoHeader_shouldUseRemoteAddress() {
            var config = new RateLimitConfig(1);

            assertThat(config.clientIp(exchange(null))).isEqualTo("198.51.100.4");
        }
    }
}
