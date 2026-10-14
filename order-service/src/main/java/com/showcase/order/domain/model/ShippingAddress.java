package com.showcase.order.domain.model;

/** Copia de la dirección principal del cliente al crear el pedido. */
public record ShippingAddress(String street, String city, String postalCode, String country) {
}
