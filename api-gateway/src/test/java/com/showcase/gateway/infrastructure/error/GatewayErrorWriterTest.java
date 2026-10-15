package com.showcase.gateway.infrastructure.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayErrorWriterTest {

    @Nested
    @DisplayName("path")
    class Path {

        @Test
        @DisplayName("en el fallback del circuit breaker devuelve la ruta que llamó el cliente, no /fallback/...")
        void path_afterForwardToFallback_shouldReturnOriginalPath() {
            // given
            MockServerWebExchange exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.get("/fallback/product-service"));
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR,
                    new LinkedHashSet<>(List.of(
                            URI.create("http://localhost:8080/api/v1/products/1?expand=true"),
                            URI.create("lb://product-service/api/v1/products/1"))));

            // when
            String path = GatewayErrorWriter.path(exchange);

            // then
            assertThat(path).isEqualTo("/api/v1/products/1");
        }

        @Test
        @DisplayName("sin reenvío interno devuelve la ruta de la petición")
        void path_withoutOriginalUrl_shouldReturnRequestPath() {
            // given
            MockServerWebExchange exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders/7"));

            // when
            String path = GatewayErrorWriter.path(exchange);

            // then
            assertThat(path).isEqualTo("/api/v1/orders/7");
        }
    }
}
