package com.showcase.order.infrastructure.persistence.repository;

import com.showcase.order.infrastructure.persistence.entity.OrderEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * En los listados las líneas se cargan por lotes ({@code default_batch_fetch_size}): un fetch join de una
 * colección con paginación obligaría a Hibernate a paginar en memoria.
 */
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findByIdAndActiveTrue(Long id);
}
