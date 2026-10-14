package com.showcase.product.infrastructure.persistence.repository;

import com.showcase.product.infrastructure.persistence.entity.CategoryEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategoryJpaRepository
        extends JpaRepository<CategoryEntity, Long>, JpaSpecificationExecutor<CategoryEntity> {

    Optional<CategoryEntity> findByIdAndActiveTrue(Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
