package com.showcase.customer.infrastructure.persistence.repository;

import com.showcase.customer.infrastructure.persistence.entity.CustomerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long>, JpaSpecificationExecutor<CustomerEntity> {

    @EntityGraph(attributePaths = "addresses")
    Optional<CustomerEntity> findByIdAndActiveTrue(Long id);

    @EntityGraph(attributePaths = "addresses")
    Optional<CustomerEntity> findByUserIdAndActiveTrue(Long userId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByUserId(Long userId);
}
