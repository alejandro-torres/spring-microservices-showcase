package com.showcase.product.testdata;

import com.showcase.product.domain.model.Category;
import com.showcase.product.domain.model.Product;
import java.math.BigDecimal;
import java.time.Instant;

/** Datos de prueba de product-service ya persistidos (con id y versión). */
public final class ProductMother {

    public static final Instant CREATED_AT = Instant.parse("2026-10-01T09:00:00Z");

    private ProductMother() {
    }

    public static Category category(long id, String name) {
        return new Category(id, name, "Categoría " + name, true, 0L, CREATED_AT, CREATED_AT);
    }

    public static Category laptops() {
        return category(1L, "Portátiles");
    }

    public static Product product(long id, String name, String price, int stock) {
        return new Product(id, name, "Descripción de " + name, new BigDecimal(price), stock, laptops(),
                true, 0L, CREATED_AT, CREATED_AT);
    }

    public static Product laptop() {
        return product(10L, "Portátil Pro 15", "1499.00", 5);
    }
}
