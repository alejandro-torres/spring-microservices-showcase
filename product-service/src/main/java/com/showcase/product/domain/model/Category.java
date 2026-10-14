package com.showcase.product.domain.model;

import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.common.domain.model.AuditedModel;
import com.showcase.product.domain.exception.ProductErrorCode;
import java.time.Instant;

/**
 * Categoría del catálogo (p. ej. Portátiles, Ropa).
 */
public class Category extends AuditedModel {

    static final int MIN_NAME = 2;
    static final int MAX_NAME = 60;

    private String name;
    private String description;

    public Category(Long id, String name, String description, boolean active, Long version,
                    Instant createdAt, Instant updatedAt) {
        super(id, active, version, createdAt, updatedAt);
        this.name = name;
        this.description = description;
    }

    public static Category create(String name, String description) {
        return new Category(null, normalizeName(name), description, true, null, null, null);
    }

    public void update(String newName, String newDescription) {
        this.name = normalizeName(newName);
        this.description = newDescription;
    }

    public static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_NAME || normalized.length() > MAX_NAME) {
            throw new InvalidRequestException(ProductErrorCode.INVALID_CATEGORY_NAME, name);
        }
        return normalized;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
