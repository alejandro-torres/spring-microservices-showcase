package com.showcase.product.application.service.impl;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.application.service.CategoryService;
import com.showcase.product.domain.exception.CategoryAlreadyExistsException;
import com.showcase.product.domain.exception.CategoryHasProductsException;
import com.showcase.product.domain.exception.CategoryNotFoundException;
import com.showcase.product.domain.model.Category;
import com.showcase.product.domain.repository.CategoryRepository;
import com.showcase.product.domain.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public Category create(String name, String description) {
        Category category = Category.create(name, description);
        if (categoryRepository.existsByName(category.getName())) {
            throw new CategoryAlreadyExistsException(category.getName());
        }
        return categoryRepository.save(category);
    }

    @Override
    public Category findById(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    }

    @Override
    public PageResult<Category> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        return categoryRepository.findAll(filters, pageQuery, includeInactive);
    }

    @Override
    @Transactional
    public Category update(Long id, String name, String description, Long version) {
        Category category = findById(id);
        category.checkVersion(version);
        category.update(name, description);
        if (categoryRepository.existsByNameAndIdNot(category.getName(), id)) {
            throw new CategoryAlreadyExistsException(category.getName());
        }
        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = findById(id);
        if (productRepository.existsActiveByCategoryId(id)) {
            throw new CategoryHasProductsException(category.getName());
        }
        category.deactivate();
        categoryRepository.save(category);
    }
}
