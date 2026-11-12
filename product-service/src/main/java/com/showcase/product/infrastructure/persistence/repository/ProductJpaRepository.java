package com.showcase.product.infrastructure.persistence.repository;

import com.showcase.product.infrastructure.persistence.entity.ProductEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

    @EntityGraph(attributePaths = "category")
    Optional<ProductEntity> findByIdAndActiveTrue(Long id);

    /** Active or not (to return stock of products already deleted). */
    @EntityGraph(attributePaths = "category")
    Optional<ProductEntity> findWithCategoryById(Long id);

    /** Listing with dynamic filters, loading the category in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<ProductEntity> findAll(Specification<ProductEntity> specification, Pageable pageable);

    /**
     * Paginated JPQL example: the {@code join fetch} brings the category in the same query, which is why it
     * needs its own {@code countQuery} (a fetch cannot be used in the count query).
     */
    @Query(value = "select p from ProductEntity p join fetch p.category c "
            + "where c.id = :categoryId and p.active = true",
            countQuery = "select count(p) from ProductEntity p where p.category.id = :categoryId and p.active = true")
    Page<ProductEntity> findActiveByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByCategoryIdAndActiveTrue(Long categoryId);
}
