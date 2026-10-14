package com.showcase.common.infrastructure.persistence.specification.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "test_products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    private Instant createdAt;
    private boolean active = true;

    @ManyToOne
    private CategoryEntity category;

    @ManyToMany
    @JoinTable(name = "test_product_labels")
    private Set<LabelEntity> labels = new HashSet<>();

    protected ProductEntity() {
    }

    public ProductEntity(String name, String description, BigDecimal price, ProductStatus status,
                         Instant createdAt, CategoryEntity category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
        this.category = category;
    }

    public ProductEntity withLabels(LabelEntity... newLabels) {
        labels.addAll(Set.of(newLabels));
        return this;
    }

    public ProductEntity inactive() {
        this.active = false;
        return this;
    }

    public String getName() {
        return name;
    }
}
