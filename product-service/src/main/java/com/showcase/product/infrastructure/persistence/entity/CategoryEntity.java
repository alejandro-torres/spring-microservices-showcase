package com.showcase.product.infrastructure.persistence.entity;

import com.showcase.common.infrastructure.persistence.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class CategoryEntity extends AuditedEntity {

    @Column(nullable = false, length = 60)
    private String name;

    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
