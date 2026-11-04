package com.showcase.order.infrastructure.persistence.repository;

import com.showcase.order.infrastructure.persistence.entity.OrderEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * In listings the lines are loaded in batches ({@code default_batch_fetch_size}): a fetch join of a
 * collection with pagination would force Hibernate to paginate in memory.
 */
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findByIdAndActiveTrue(Long id);
}
