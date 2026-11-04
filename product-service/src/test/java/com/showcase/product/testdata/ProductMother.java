package com.showcase.product.testdata;

import com.showcase.product.domain.model.Category;
import com.showcase.product.domain.model.Product;
import java.math.BigDecimal;
import java.time.Instant;

/** product-service test data, already persisted (with id and version). */
public final class ProductMother {

    public static final Instant CREATED_AT = Instant.parse("2026-10-01T09:00:00Z");

    private ProductMother() {
    }

    public static Category category(long id, String name) {
        return new Category(id, name, "Category " + name, true, 0L, CREATED_AT, CREATED_AT);
    }

    public static Category laptops() {
        return category(1L, "Laptops");
    }

    public static Product product(long id, String name, String price, int stock) {
        return new Product(id, name, "Description of " + name, new BigDecimal(price), stock, laptops(),
                true, 0L, CREATED_AT, CREATED_AT);
    }

    public static Product laptop() {
        return product(10L, "Laptop Pro 15", "1499.00", 5);
    }
}
