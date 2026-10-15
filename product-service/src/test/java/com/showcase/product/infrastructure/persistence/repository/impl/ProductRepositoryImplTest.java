package com.showcase.product.infrastructure.persistence.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.filter.FilterOperator;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import com.showcase.product.domain.model.Product;
import com.showcase.product.infrastructure.persistence.entity.CategoryEntity;
import com.showcase.product.infrastructure.persistence.entity.ProductEntity;
import com.showcase.product.infrastructure.persistence.mapper.CategoryEntityMapperImpl;
import com.showcase.product.infrastructure.persistence.mapper.ProductEntityMapperImpl;
import com.showcase.product.infrastructure.persistence.repository.CategoryJpaRepository;
import com.showcase.product.infrastructure.persistence.repository.ProductJpaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * El adaptador de persistencia sin base de datos: convierte {@code PageQuery} → {@code Pageable} y
 * {@code Page<ProductEntity>} → {@code PageResult<Product>}. El repositorio de Spring Data se simula y devuelve un
 * {@link PageImpl} real.
 */
@ExtendWith(MockitoExtension.class)
class ProductRepositoryImplTest {

    @Mock
    private ProductJpaRepository productJpaRepository;

    @Mock
    private CategoryJpaRepository categoryJpaRepository;

    private ProductRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        var mapper = new ProductEntityMapperImpl();
        ReflectionTestUtils.setField(mapper, "categoryEntityMapper", new CategoryEntityMapperImpl());
        repository = new ProductRepositoryImpl(productJpaRepository, categoryJpaRepository, mapper,
                new GenericSpecificationBuilder(true));
    }

    private static ProductEntity entity(long id, String name) {
        var category = new CategoryEntity();
        category.setId(1L);
        category.setName("Portátiles");
        var entity = new ProductEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setPrice(new BigDecimal("10.00"));
        entity.setStock(3);
        entity.setCategory(category);
        return entity;
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("convierte la página pedida en Pageable (con el orden y su desempate por id) y el resultado en "
                + "PageResult")
        @SuppressWarnings("unchecked")
        void findAll_shouldConvertPageQueryAndPage() {
            // given
            var query = new PageQuery(1, 2, "price", SortDirection.DESC);
            var captor = ArgumentCaptor.forClass(Pageable.class);
            given(productJpaRepository.findAll(any(Specification.class), captor.capture()))
                    .willAnswer(invocation -> new PageImpl<>(List.of(entity(3L, "Ratón"), entity(4L, "Teclado")),
                            invocation.getArgument(1), 5));

            // when
            PageResult<Product> result = repository.findAll(
                    List.of(FilterCriterion.of("stock", FilterOperator.GT, 0)), query, false);

            // then
            Pageable pageable = captor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(1);
            assertThat(pageable.getPageSize()).isEqualTo(2);
            assertThat(pageable.getSort().getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
            assertThat(result.content()).extracting(Product::getName).containsExactly("Ratón", "Teclado");
            assertThat(result.totalElements()).isEqualTo(5);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.first()).isFalse();
            assertThat(result.last()).isFalse();
        }

        @Test
        @DisplayName("una página vacía devuelve un PageResult vacío")
        void findActiveByCategory_withNoResults_shouldReturnEmptyPage() {
            // given
            given(productJpaRepository.findActiveByCategoryId(eq(9L), any(Pageable.class)))
                    .willAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

            // when
            PageResult<Product> result = repository.findActiveByCategory(9L,
                    new PageQuery(0, 10, "id", SortDirection.ASC));

            // then
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("convierte la entidad con su categoría, o devuelve vacío si no existe o está borrado")
        void findById_shouldMapEntityOrReturnEmpty() {
            // given
            given(productJpaRepository.findByIdAndActiveTrue(3L)).willReturn(Optional.of(entity(3L, "Ratón")));
            given(productJpaRepository.findByIdAndActiveTrue(99L)).willReturn(Optional.empty());

            // when / then
            assertThat(repository.findById(3L)).hasValueSatisfying(product -> {
                assertThat(product.getName()).isEqualTo("Ratón");
                assertThat(product.getCategory().getName()).isEqualTo("Portátiles");
            });
            assertThat(repository.findById(99L)).isEmpty();
        }
    }
}
