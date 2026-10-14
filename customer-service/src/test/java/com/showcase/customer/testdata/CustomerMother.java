package com.showcase.customer.testdata;

import com.showcase.customer.domain.model.Address;
import com.showcase.customer.domain.model.Customer;
import java.time.Instant;
import java.util.List;

public final class CustomerMother {

    public static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    private CustomerMother() {
    }

    /** Cliente guardado (id 1, versión 0) con dos direcciones: la 10 (principal) y la 11. */
    public static Customer ana() {
        return new Customer(1L, 7L, "Ana", "García", "ana@example.com", "600111222",
                List.of(new Address(10L, "Gran Vía 1", "Madrid", "28013", "ES", true),
                        new Address(11L, "Diagonal 2", "Barcelona", "08019", "ES", false)),
                true, 0L, CREATED_AT, CREATED_AT);
    }

    public static Address newAddress() {
        return Address.create("Calle Mayor 3", "Sevilla", "41001", "es");
    }
}
