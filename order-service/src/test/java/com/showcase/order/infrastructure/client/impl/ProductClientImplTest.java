package com.showcase.order.infrastructure.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ExternalServiceException;
import com.showcase.common.domain.exception.ForbiddenException;
import com.showcase.order.infrastructure.client.config.CircuitBreakerProperties;
import com.showcase.order.infrastructure.client.config.ClientConfig;
import com.showcase.order.infrastructure.client.dto.ProductClientResponseDTO;
import com.showcase.order.infrastructure.client.feign.ProductFeignClient;
import com.showcase.order.infrastructure.client.feign.RemoteResourceNotFoundException;
import feign.RetryableException;
import feign.Request;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Circuit breaker real (sin Spring) con una ventana pequeña: se abre tras 4 llamadas con al menos 50 % de fallos. */
class ProductClientImplTest {

    private final ProductFeignClient feignClient = mock(ProductFeignClient.class);
    private CircuitBreakerRegistry registry;
    private ProductClientImpl client;

    @BeforeEach
    void setUp() {
        registry = new ClientConfig().circuitBreakerRegistry(
                new CircuitBreakerProperties(4, 4, 50, Duration.ofMinutes(1), 1));
        client = new ProductClientImpl(feignClient, registry);
    }

    private static RetryableException timeout() {
        var request = Request.create(Request.HttpMethod.GET, "http://product-service", Map.of(), null, null, null);
        return new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, (Long) null, request);
    }

    private CircuitBreaker.State state() {
        return registry.circuitBreaker("product-service").getState();
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("convierte la respuesta en ProductInfo")
        void findById_shouldMapResponse() {
            given(feignClient.findById(1L)).willReturn(new ProductClientResponseDTO(1L, "Ratón", BigDecimal.TEN, 3));

            assertThat(client.findById(1L)).hasValueSatisfying(product -> {
                assertThat(product.name()).isEqualTo("Ratón");
                assertThat(product.stock()).isEqualTo(3);
            });
        }

        @Test
        @DisplayName("un 404 devuelve vacío y no cuenta como fallo del servicio")
        void findById_notFound_shouldReturnEmpty() {
            given(feignClient.findById(1L)).willThrow(new RemoteResourceNotFoundException("findById"));

            for (int i = 0; i < 5; i++) {
                assertThat(client.findById(1L)).isEmpty();
            }
            assertThat(state()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("un timeout se convierte en 503")
        void findById_timeout_shouldThrowServiceUnavailable() {
            given(feignClient.findById(1L)).willThrow(timeout());

            assertThatThrownBy(() -> client.findById(1L))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("tras varios fallos el circuito se abre y ya no se llama al servicio")
        void findById_repeatedFailures_shouldOpenCircuit() {
            given(feignClient.findById(1L)).willThrow(timeout());
            for (int i = 0; i < 4; i++) {
                assertThatThrownBy(() -> client.findById(1L)).isInstanceOf(ExternalServiceException.class);
            }

            assertThatThrownBy(() -> client.findById(1L)).isInstanceOf(ExternalServiceException.class);

            assertThat(state()).isEqualTo(CircuitBreaker.State.OPEN);
            verify(feignClient, times(4)).findById(1L);
        }

        @Test
        @DisplayName("un 403 se propaga tal cual y no abre el circuito")
        void findById_forbidden_shouldNotOpenCircuit() {
            given(feignClient.findById(1L)).willThrow(new ForbiddenException(CommonErrorCode.FORBIDDEN));

            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> client.findById(1L)).isInstanceOf(ForbiddenException.class);
            }
            assertThat(state()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }
}
