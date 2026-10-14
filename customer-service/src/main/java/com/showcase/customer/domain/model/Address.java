package com.showcase.customer.domain.model;

import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import java.util.Locale;

/**
 * Dirección de un cliente. Solo existe dentro de su cliente (composición).
 */
public class Address {

    private final Long id;
    private final String street;
    private final String city;
    private final String postalCode;
    private final String country;
    private boolean main;

    public Address(Long id, String street, String city, String postalCode, String country, boolean main) {
        this.id = id;
        this.street = street;
        this.city = city;
        this.postalCode = postalCode;
        this.country = country;
        this.main = main;
    }

    public static Address create(String street, String city, String postalCode, String country) {
        return new Address(null, required(street, "street"), required(city, "city"), required(postalCode, "postalCode"),
                required(country, "country").toUpperCase(Locale.ROOT), false);
    }

    void markAsMain(boolean isMain) {
        this.main = isMain;
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(CustomerErrorCode.INVALID_CUSTOMER_DATA, field);
        }
        return value.trim();
    }

    public Long getId() {
        return id;
    }

    public String getStreet() {
        return street;
    }

    public String getCity() {
        return city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    public boolean isMain() {
        return main;
    }
}
