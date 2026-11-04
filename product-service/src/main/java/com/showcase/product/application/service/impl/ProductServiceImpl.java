package com.showcase.product.application.service.impl;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.application.service.ProductService;
import com.showcase.product.domain.exception.CategoryNotFoundException;
import com.showcase.product.domain.exception.ProductAlreadyExistsException;
import com.showcase.product.domain.exception.ProductNotFoundException;
import com.showcase.product.domain.model.Category;
import com.showcase.product.domain.model.Product;
import com.showcase.product.domain.repository.CategoryRepository;
import com.showcase.product.domain.repository.ProductRepository;
import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads in read-only transactions (at class level) and one transaction per write.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /** {@code timeout}: a catalog write should never hold the connection for more than 5 s. */
    @Override
    @Transactional(timeout = 5)
    public Product create(String name, String description, BigDecimal price, int initialStock, Long categoryId) {
        Category category = findCategory(categoryId);
        Product product = Product.create(name, description, price, initialStock, category);
        if (productRepository.existsByName(product.getName())) {
            throw new ProductAlreadyExistsException(product.getName());
        }
        return productRepository.save(product);
    }

    @Override
    public Product findById(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    @Observed(name = "product.search", contextualName = "search products")
    public PageResult<Product> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        return productRepository.findAll(filters, pageQuery, includeInactive);
    }

    @Override
    public PageResult<Product> findByCategory(Long categoryId, PageQuery pageQuery) {
        findCategory(categoryId);
        return productRepository.findActiveByCategory(categoryId, pageQuery);
    }

    @Override
    @Transactional(timeout = 5)
    public Product update(Long id, String name, String description, BigDecimal price, Long categoryId,
                          Long version) {
        Product product = findById(id);
        product.checkVersion(version);
        Category category = findCategory(categoryId);
        product.update(name, description, price, category);
        if (productRepository.existsByNameAndIdNot(product.getName(), id)) {
            throw new ProductAlreadyExistsException(product.getName());
        }
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        product.deactivate();
        productRepository.save(product);
    }

    /**
     * Two simultaneous adjustments of the same product do not overwrite each other: the second to save fails on the
     * entity's version (@Version) and the API responds 409 so it can be retried with fresh data.
     */
    @Override
    @Transactional
    public Product adjustStock(Long id, int delta) {
        Product product = findById(id);
        product.adjustStock(delta);
        return productRepository.save(product);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }
}
