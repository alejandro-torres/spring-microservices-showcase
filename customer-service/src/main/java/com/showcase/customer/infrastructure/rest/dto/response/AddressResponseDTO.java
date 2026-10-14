package com.showcase.customer.infrastructure.rest.dto.response;

public record AddressResponseDTO(Long id, String street, String city, String postalCode, String country, boolean main) {
}
