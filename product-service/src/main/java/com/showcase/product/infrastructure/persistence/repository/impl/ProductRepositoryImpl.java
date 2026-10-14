package com.showcase.product.infrastructure.persistence.repository.impl;

import static com.showcase.product.infrastructure.persistence.specification.ProductFilterMappings.PRODUCTS;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.persistence.page.SpringDataPageAdapter;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import com.showcase.product.domain.exception.ProductNotFoundException;
import com.showcase.product.domain.model.Product;
import com.showcase.product.domain.repository.ProductRepository;
import com.showcase.product.infrastructure.persistence.entity.ProductEntity;
import com.showcase.product.infrastructure.persistence.mapper.ProductEntityMapper;
import com.showcase.product.infrastructure.persistence.repository.CategoryJpaRepository;
import com.showcase.product.infrastructure.persistence.repository.ProductJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia de productos con Spring Data JPA. Participa en la transacción del servicio.
 */
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final ProductEntityMapper mapper;
    private final GenericSpecificationBuilder specificationBuilder;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository, CategoryJpaRepository categoryJpaRepository,
                                 ProductEntityMapper mapper, GenericSpecificationBuilder specificationBuilder) {
        this.productJpaRepository = productJpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
        this.mapper = mapper;
        this.specificationBuilder = specificationBuilder;
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity;
        if (product.getId() == null) {
            entity = mapper.toEntity(product);
        } else {
            entity = productJpaRepository.findById(product.getId())
                    .orElseThrow(() -> new ProductNotFoundException(product.getId()));
            mapper.updateEntity(product, entity);
        }
        entity.setCategory(categoryJpaRepository.getReferenceById(product.getCategory().getId()));
        return mapper.toDomain(productJpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndActiveTrue(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<Product> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        Page<ProductEntity> page = productJpaRepository.findAll(
                specificationBuilder.build(filters, PRODUCTS, includeInactive),
                SpringDataPageAdapter.toPageable(pageQuery, PRODUCTS));
        return SpringDataPageAdapter.toPageResult(page, mapper::toDomain);
    }

    @Override
    public PageResult<Product> findActiveByCategory(Long categoryId, PageQuery pageQuery) {
        Page<ProductEntity> page = productJpaRepository.findActiveByCategoryId(categoryId,
                SpringDataPageAdapter.toPageable(pageQuery, PRODUCTS));
        return SpringDataPageAdapter.toPageResult(page, mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return productJpaRepository.existsByNameIgnoreCase(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return productJpaRepository.existsByNameIgnoreCaseAndIdNot(name, id);
    }

    @Override
    public boolean existsActiveByCategoryId(Long categoryId) {
        return productJpaRepository.existsByCategoryIdAndActiveTrue(categoryId);
    }
}
