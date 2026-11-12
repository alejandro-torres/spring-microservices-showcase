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
        @DisplayName("without trusted proxies it uses the connection IP and ignores X-Forwarded-For (it can be forged)")
        void clientIp_withoutTrustedProxies_shouldIgnoreForwardedFor() {
            var config = new RateLimitConfig(0);

            assertThat(config.clientIp(exchange("203.0.113.7"))).isEqualTo("198.51.100.4");
        }

        @Test
        @DisplayName("with a trusted proxy it uses the IP that proxy added, not those the client invents")
        void clientIp_withOneTrustedProxy_shouldUseLastForwardedAddress() {
            var config = new RateLimitConfig(1);

            // The client sends a forged "1.2.3.4"; the proxy appends the real IP 203.0.113.7 at the end.
            assertThat(config.clientIp(exchange("1.2.3.4, 203.0.113.7"))).isEqualTo("203.0.113.7");
        }

        @Test
        @DisplayName("with a trusted proxy but no header, it uses the connection IP")
        void clientIp_withTrustedProxyButNoHeader_shouldUseRemoteAddress() {
            var config = new RateLimitConfig(1);

            assertThat(config.clientIp(exchange(null))).isEqualTo("198.51.100.4");
        }
    }
}
