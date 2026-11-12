package com.showcase.product.application.service.impl;

import static com.showcase.product.testdata.ProductMother.laptops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.domain.exception.CategoryAlreadyExistsException;
import com.showcase.product.domain.exception.CategoryHasProductsException;
import com.showcase.product.domain.exception.CategoryNotFoundException;
import com.showcase.product.domain.exception.ProductErrorCode;
import com.showcase.product.domain.model.Category;
import com.showcase.product.domain.repository.CategoryRepository;
import com.showcase.product.domain.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    @DisplayName("creates a category and rejects a repeated name")
    void create_shouldCheckUniqueName() {
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(categoryService.create(" Audio ", null).getName()).isEqualTo("Audio");

        given(categoryRepository.existsByName("Laptops")).willReturn(true);
        assertThatThrownBy(() -> categoryService.create("Laptops", null))
                .isInstanceOf(CategoryAlreadyExistsException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.CATEGORY_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("findById throws CATEGORY_NOT_FOUND and findAll delegates to the repository")
    void find_shouldThrowOrDelegate() {
        var page = PageResult.of(List.of(laptops()), 0, 10, 1);
        given(categoryRepository.findAll(List.of(), PageQuery.firstPage(), false)).willReturn(page);

        assertThatThrownBy(() -> categoryService.findById(9L)).isInstanceOf(CategoryNotFoundException.class);
        assertThat(categoryService.findAll(List.of(), PageQuery.firstPage(), false)).isSameAs(page);
    }

    @Test
    @DisplayName("update checks the version and the uniqueness of the new name")
    void update_shouldCheckVersionAndName() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(laptops()));
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(categoryService.update(1L, "Laptop computers", null, 0L).getName())
                .isEqualTo("Laptop computers");
        assertThatThrownBy(() -> categoryService.update(1L, "Other", null, 7L)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("delete rejects a category with active products (409) and otherwise does a soft delete")
    void delete_shouldCheckProducts() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(laptops()));
        given(productRepository.existsActiveByCategoryId(1L)).willReturn(true, false);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(CategoryHasProductsException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.CATEGORY_HAS_PRODUCTS);
        verify(categoryRepository, never()).save(any());

        categoryService.delete(1L);
        var captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }
}
