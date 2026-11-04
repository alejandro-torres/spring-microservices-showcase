package com.showcase.order.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The part of customer-service's response that order-service uses; the other fields are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerClientResponseDTO(Long id, String email, List<AddressClientResponseDTO> addresses) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddressClientResponseDTO(String street, String city, String postalCode, String country,
                                           boolean main) {
    }
}
