package com.showcase.order.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Parte de la respuesta de customer-service que usa order-service; el resto de campos se ignora. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerClientResponseDTO(Long id, String email, List<AddressClientResponseDTO> addresses) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddressClientResponseDTO(String street, String city, String postalCode, String country,
                                           boolean main) {
    }
}
