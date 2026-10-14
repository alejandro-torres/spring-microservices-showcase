package com.showcase.order.infrastructure.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.showcase.order.infrastructure.client.dto.CustomerClientResponseDTO;
import com.showcase.order.infrastructure.client.dto.CustomerClientResponseDTO.AddressClientResponseDTO;
import com.showcase.order.infrastructure.client.feign.CustomerFeignClient;
import com.showcase.order.infrastructure.client.feign.RemoteResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CustomerClientImplTest {

    private final CustomerFeignClient feignClient = mock(CustomerFeignClient.class);
    private final CustomerClientImpl client = new CustomerClientImpl(feignClient, CircuitBreakerRegistry.ofDefaults());

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("usa la dirección principal como dirección de envío")
        void findById_shouldPickMainAddress() {
            given(feignClient.findById(3L)).willReturn(new CustomerClientResponseDTO(3L, "c@example.com", List.of(
                    new AddressClientResponseDTO("Calle 1", "Lugo", "27001", "ES", false),
                    new AddressClientResponseDTO("Calle 2", "Soria", "42001", "ES", true))));

            assertThat(client.findById(3L)).hasValueSatisfying(customer ->
                    assertThat(customer.mainAddress()).hasValueSatisfying(
                            address -> assertThat(address.city()).isEqualTo("Soria")));
        }

        @Test
        @DisplayName("un cliente sin direcciones no tiene dirección de envío")
        void findById_withoutAddresses_shouldHaveNoMainAddress() {
            given(feignClient.findById(3L)).willReturn(new CustomerClientResponseDTO(3L, "c@example.com", null));

            assertThat(client.findById(3L)).hasValueSatisfying(
                    customer -> assertThat(customer.mainAddress()).isEmpty());
        }

        @Test
        @DisplayName("un 404 devuelve vacío")
        void findById_notFound_shouldReturnEmpty() {
            given(feignClient.findById(3L)).willThrow(new RemoteResourceNotFoundException("findById"));

            assertThat(client.findById(3L)).isEmpty();
        }
    }
}
