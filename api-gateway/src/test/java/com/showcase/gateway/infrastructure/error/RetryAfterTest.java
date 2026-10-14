package com.showcase.gateway.infrastructure.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class RetryAfterTest {

    @Nested
    @DisplayName("seconds")
    class Seconds {

        @Test
        @DisplayName("login: 12 fichas por petición y 1 por segundo → 12 s")
        void seconds_forLoginLimit_shouldBeTwelve() {
            assertThat(RetryAfter.seconds(headers("12", "1"))).isEqualTo(12);
        }

        @Test
        @DisplayName("redondea hacia arriba y nunca baja de 1 s")
        void seconds_shouldRoundUpWithMinimumOfOne() {
            assertThat(RetryAfter.seconds(headers("3", "2"))).isEqualTo(2);
            assertThat(RetryAfter.seconds(headers("1", "20"))).isEqualTo(1);
        }

        @Test
        @DisplayName("sin cabeceras válidas devuelve 1 s")
        void seconds_withoutHeaders_shouldDefaultToOne() {
            assertThat(RetryAfter.seconds(new HttpHeaders())).isEqualTo(1);
            assertThat(RetryAfter.seconds(headers("0", "0"))).isEqualTo(1);
        }
    }

    private static HttpHeaders headers(String requested, String replenishRate) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Requested-Tokens", requested);
        headers.set("X-RateLimit-Replenish-Rate", replenishRate);
        return headers;
    }
}
