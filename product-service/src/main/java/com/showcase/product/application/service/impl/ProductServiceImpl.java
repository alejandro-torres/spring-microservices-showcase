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
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecturas en transacciones de solo lectura (a nivel de clase) y una transacción por escritura.
 */
@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductServiceImpl(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    /** {@code timeout}: una escritura del catálogo nunca debería tener la conexión ocupada más de 5 s. */
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
     * Dos ajustes simultáneos del mismo producto no se pisan: el segundo en guardar falla por la
     * versión de la entidad (@Version) y la API responde 409 para que se reintente con datos frescos.
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
