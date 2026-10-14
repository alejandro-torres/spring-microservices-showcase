package com.showcase.product.application.service.impl;

import static com.showcase.product.testdata.ProductMother.category;
import static com.showcase.product.testdata.ProductMother.laptop;
import static com.showcase.product.testdata.ProductMother.laptops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.domain.exception.CategoryNotFoundException;
import com.showcase.product.domain.exception.InsufficientStockException;
import com.showcase.product.domain.exception.ProductAlreadyExistsException;
import com.showcase.product.domain.exception.ProductErrorCode;
import com.showcase.product.domain.exception.ProductNotFoundException;
import com.showcase.product.domain.model.Product;
import com.showcase.product.domain.repository.CategoryRepository;
import com.showcase.product.domain.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Captor
    private ArgumentCaptor<Product> productCaptor;

    private void givenSaveReturnsArgument() {
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("crea el producto en su categoría")
        void create_shouldSaveProduct() {
            given(categoryRepository.findById(1L)).willReturn(Optional.of(laptops()));
            givenSaveReturnsArgument();

            var created = productService.create("Portátil Air 13", "Ligero", new BigDecimal("999"), 3, 1L);

            assertThat(created.getCategory().getName()).isEqualTo("Portátiles");
            assertThat(created.getPrice()).isEqualByComparingTo("999.00");
        }

        @Test
        @DisplayName("lanza CATEGORY_NOT_FOUND si la categoría no existe y no guarda nada")
        void create_withMissingCategory_shouldThrow() {
            assertThatThrownBy(() -> productService.create("Portátil", null, BigDecimal.TEN, 1, 9L))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.CATEGORY_NOT_FOUND);
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("rechaza un nombre ya existente comparando el nombre ya normalizado")
        void create_withExistingName_shouldThrow() {
            given(categoryRepository.findById(1L)).willReturn(Optional.of(laptops()));
            given(productRepository.existsByName("Portátil Pro 15")).willReturn(true);

            assertThatThrownBy(() -> productService.create("  Portátil   Pro 15 ", null, BigDecimal.TEN, 1, 1L))
                    .isInstanceOf(ProductAlreadyExistsException.class);
            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("consultas")
    class Find {

        @Test
        @DisplayName("findById lanza PRODUCT_NOT_FOUND si no existe")
        void findById_missing_shouldThrow() {
            assertThatThrownBy(() -> productService.findById(99L)).isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("findByCategory comprueba la categoría y delega en la consulta JPQL")
        void findByCategory_shouldCheckCategory() {
            var page = PageResult.of(List.of(laptop()), 0, 10, 1);
            given(categoryRepository.findById(1L)).willReturn(Optional.of(laptops()));
            given(productRepository.findActiveByCategory(1L, PageQuery.firstPage())).willReturn(page);

            assertThat(productService.findByCategory(1L, PageQuery.firstPage())).isSameAs(page);
            assertThatThrownBy(() -> productService.findByCategory(2L, PageQuery.firstPage()))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update y delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("actualiza datos y categoría sin tocar el stock")
        void update_shouldKeepStock() {
            given(productRepository.findById(10L)).willReturn(Optional.of(laptop()));
            given(categoryRepository.findById(2L)).willReturn(Optional.of(category(2L, "Accesorios")));
            givenSaveReturnsArgument();

            var updated = productService.update(10L, "Portátil Pro 16", null, new BigDecimal("1599.99"), 2L, 0L);

            assertThat(updated.getName()).isEqualTo("Portátil Pro 16");
            assertThat(updated.getCategory().getName()).isEqualTo("Accesorios");
            assertThat(updated.getStock()).isEqualTo(5);
        }

        @Test
        @DisplayName("rechaza una versión desactualizada con CONCURRENT_MODIFICATION")
        void update_withStaleVersion_shouldThrow() {
            given(productRepository.findById(10L)).willReturn(Optional.of(laptop()));

            assertThatThrownBy(() -> productService.update(10L, "Otro", null, BigDecimal.TEN, 1L, 4L))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.CONCURRENT_MODIFICATION);
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("rechaza renombrar a un nombre que ya usa otro producto")
        void update_withNameOfAnotherProduct_shouldThrow() {
            given(productRepository.findById(10L)).willReturn(Optional.of(laptop()));
            given(categoryRepository.findById(1L)).willReturn(Optional.of(laptops()));
            given(productRepository.existsByNameAndIdNot("Portátil Air 13", 10L)).willReturn(true);

            assertThatThrownBy(() -> productService.update(10L, "Portátil Air 13", null, BigDecimal.TEN, 1L, 0L))
                    .isInstanceOf(ProductAlreadyExistsException.class);
        }

        @Test
        @DisplayName("delete hace un borrado lógico")
        void delete_shouldDeactivate() {
            given(productRepository.findById(10L)).willReturn(Optional.of(laptop()));

            productService.delete(10L);

            verify(productRepository).save(productCaptor.capture());
            assertThat(productCaptor.getValue().isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("adjustStock")
    class AdjustStock {

        @Test
        @DisplayName("aplica el ajuste y guarda")
        void adjustStock_shouldUpdateStock() {
            given(productRepository.findById(10L)).willReturn(Optional.of(laptop()));
            givenSaveReturnsArgument();

            assertThat(productService.adjustStock(10L, -3).getStock()).isEqualTo(2);
        }

        @Test
        @DisplayName("no guarda nada si el ajuste dejaría el stock en negativo")
        void adjustStock_belowZero_shouldThrow() {
            given(productRepository.findById(10L)).willReturn(Optional.of(laptop()));

            assertThatThrownBy(() -> productService.adjustStock(10L, -6)).isInstanceOf(InsufficientStockException.class);
            verify(productRepository, never()).save(any());
        }
    }
}
