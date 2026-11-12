package com.showcase.product.domain.model;

import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.common.domain.model.AuditedModel;
import com.showcase.product.domain.exception.InsufficientStockException;
import com.showcase.product.domain.exception.ProductErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Catalog product. It holds the stock rules: stock can never go negative.
 */
public class Product extends AuditedModel {

    static final int MIN_NAME = 2;
    static final int MAX_NAME = 120;
    static final BigDecimal MAX_PRICE = new BigDecimal("999999.99");

    private String name;
    private String description;
    private BigDecimal price;
    private int stock;
    private Category category;

    public Product(Long id, String name, String description, BigDecimal price, int stock, Category category,
                   boolean active, Long version, Instant createdAt, Instant updatedAt) {
        super(id, active, version, createdAt, updatedAt);
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }

    public static Product create(String name, String description, BigDecimal price, int initialStock,
                                 Category category) {
        if (initialStock < 0) {
            throw new InvalidRequestException(ProductErrorCode.INVALID_QUANTITY, initialStock);
        }
        return new Product(null, normalizeName(name), description, normalizePrice(price), initialStock,
                Objects.requireNonNull(category, "category"), true, null, null, null);
    }

    /** The stock is not touched here: it has its own operations so it is never overwritten by mistake. */
    public void update(String newName, String newDescription, BigDecimal newPrice, Category newCategory) {
        this.name = normalizeName(newName);
        this.description = newDescription;
        this.price = normalizePrice(newPrice);
        this.category = Objects.requireNonNull(newCategory, "category");
    }

    /** Reserves units for an order; fails without modifying anything if there are not enough. */
    public void reserveStock(int quantity) {
        requirePositive(quantity);
        if (stock < quantity) {
            throw new InsufficientStockException(name, stock, quantity);
        }
        stock -= quantity;
    }

    /** Returns reserved units (e.g. when cancelling an order). */
    public void releaseStock(int quantity) {
        requirePositive(quantity);
        stock += quantity;
    }

    /** Manual inventory adjustment: positive for incoming stock, negative for outgoing stock or shrinkage. */
    public void adjustStock(int delta) {
        if (delta == 0) {
            throw new InvalidRequestException(ProductErrorCode.INVALID_QUANTITY, delta);
        }
        if (stock + delta < 0) {
            throw new InsufficientStockException(name, stock, -delta);
        }
        stock += delta;
    }

    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }

    public static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_NAME || normalized.length() > MAX_NAME) {
            throw new InvalidRequestException(ProductErrorCode.INVALID_PRODUCT_NAME, name);
        }
        return normalized;
    }

    /** Price greater than 0 with at most 2 decimals (it is not silently rounded). */
    static BigDecimal normalizePrice(BigDecimal price) {
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new InvalidRequestException(ProductErrorCode.INVALID_PRICE, price);
        }
        try {
            return price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidRequestException(ProductErrorCode.INVALID_PRICE, e, price);
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new InvalidRequestException(ProductErrorCode.INVALID_QUANTITY, quantity);
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public Category getCategory() {
        return category;
    }
}
