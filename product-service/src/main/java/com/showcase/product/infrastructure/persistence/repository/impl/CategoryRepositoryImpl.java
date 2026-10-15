package com.showcase.product.infrastructure.persistence.repository.impl;

import static com.showcase.product.infrastructure.persistence.specification.ProductFilterMappings.CATEGORIES;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.persistence.page.SpringDataPageAdapter;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import com.showcase.product.domain.exception.CategoryNotFoundException;
import com.showcase.product.domain.model.Category;
import com.showcase.product.domain.repository.CategoryRepository;
import com.showcase.product.infrastructure.persistence.entity.CategoryEntity;
import com.showcase.product.infrastructure.persistence.mapper.CategoryEntityMapper;
import com.showcase.product.infrastructure.persistence.repository.CategoryJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryJpaRepository jpaRepository;
    private final CategoryEntityMapper mapper;
    private final GenericSpecificationBuilder specificationBuilder;

    @Override
    public Category save(Category category) {
        CategoryEntity entity;
        if (category.getId() == null) {
            entity = mapper.toEntity(category);
        } else {
            entity = jpaRepository.findById(category.getId())
                    .orElseThrow(() -> new CategoryNotFoundException(category.getId()));
            mapper.updateEntity(category, entity);
        }
        return mapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpaRepository.findByIdAndActiveTrue(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<Category> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        Page<CategoryEntity> page = jpaRepository.findAll(
                specificationBuilder.build(filters, CATEGORIES, includeInactive),
                SpringDataPageAdapter.toPageable(pageQuery, CATEGORIES));
        return SpringDataPageAdapter.toPageResult(page, mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByNameIgnoreCase(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return jpaRepository.existsByNameIgnoreCaseAndIdNot(name, id);
    }
}
