package com.showcase.order.infrastructure.rest.dto.response;

public record ShippingAddressResponseDTO(String street, String city, String postalCode, String country) {
}
