package com.showcase.order.domain.model;

import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.order.domain.exception.OrderErrorCode;

/** Dirección de envío: al crear el pedido es una copia de la dirección principal del cliente. */
public record ShippingAddress(String street, String city, String postalCode, String country) {

    public ShippingAddress {
        if (isBlank(street) || isBlank(city) || isBlank(postalCode) || country == null
                || !country.matches("[A-Z]{2}")) {
            throw new InvalidRequestException(OrderErrorCode.INVALID_ORDER_DATA, "shippingAddress");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
