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
 * Producto del catálogo. Contiene las reglas de stock: nunca puede quedar negativo.
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

    /** El stock no se toca aquí: tiene sus propias operaciones para que nunca se pise por error. */
    public void update(String newName, String newDescription, BigDecimal newPrice, Category newCategory) {
        this.name = normalizeName(newName);
        this.description = newDescription;
        this.price = normalizePrice(newPrice);
        this.category = Objects.requireNonNull(newCategory, "category");
    }

    /** Reserva unidades para un pedido; falla sin modificar nada si no hay suficientes. */
    public void reserveStock(int quantity) {
        requirePositive(quantity);
        if (stock < quantity) {
            throw new InsufficientStockException(name, stock, quantity);
        }
        stock -= quantity;
    }

    /** Devuelve unidades reservadas (p. ej. al cancelar un pedido). */
    public void releaseStock(int quantity) {
        requirePositive(quantity);
        stock += quantity;
    }

    /** Ajuste manual de inventario: positivo para entradas, negativo para salidas o mermas. */
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

    /** Precio mayor que 0 y con 2 decimales como máximo (no se redondea en silencio). */
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
