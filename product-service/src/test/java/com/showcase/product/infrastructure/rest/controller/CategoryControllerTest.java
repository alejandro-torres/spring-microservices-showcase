package com.showcase.product.infrastructure.rest.controller;

import static com.showcase.product.testdata.ProductMother.laptop;
import static com.showcase.product.testdata.ProductMother.laptops;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.config.CommonWebAutoConfiguration;
import com.showcase.product.application.service.CategoryService;
import com.showcase.product.application.service.ProductService;
import com.showcase.product.domain.exception.CategoryHasProductsException;
import com.showcase.product.infrastructure.rest.mapper.ProductRestMapperImpl;
import com.showcase.product.infrastructure.security.SecurityConfig;
import com.showcase.product.testdata.TestJwt;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import({CommonWebAutoConfiguration.class, SecurityConfig.class, ProductRestMapperImpl.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("POST devuelve 201 y GET por id devuelve la categoría")
    void createAndFind_shouldWork() throws Exception {
        given(categoryService.create("Portátiles", null)).willReturn(laptops());
        given(categoryService.findById(1L)).willReturn(laptops());

        mockMvc.perform(post("/api/v1/categories").with(TestJwt.catalogManager())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Portátiles"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/categories/1")));
        mockMvc.perform(get("/api/v1/categories/1").with(TestJwt.customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Portátiles"));
    }

    @Test
    @DisplayName("lista categorías con filtros y los productos de una categoría ordenados por nombre")
    void listings_shouldWork() throws Exception {
        given(categoryService.findAll(anyList(), any(PageQuery.class), anyBoolean()))
                .willReturn(PageResult.of(List.of(laptops()), 0, 10, 1));
        given(productService.findByCategory(eq(1L), eq(new PageQuery(0, 10, "name", SortDirection.ASC))))
                .willReturn(PageResult.of(List.of(laptop()), 0, 10, 1));

        mockMvc.perform(get("/api/v1/categories").with(TestJwt.customer()).param("filter", "name:like:port"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Portátiles"));
        mockMvc.perform(get("/api/v1/categories/1/products").with(TestJwt.customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].categoryName").value("Portátiles"));
    }

    @Test
    @DisplayName("PUT actualiza y DELETE devuelve 409 si la categoría tiene productos")
    void updateAndDelete_shouldWork() throws Exception {
        given(categoryService.update(1L, "Ordenadores", null, 0L)).willReturn(laptops());
        willThrow(new CategoryHasProductsException("Portátiles")).given(categoryService).delete(1L);

        mockMvc.perform(put("/api/v1/categories/1").with(TestJwt.catalogManager())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Ordenadores","version":0}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/categories/1").with(TestJwt.catalogManager()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_PRODUCTS"));
    }

    @Test
    @DisplayName("un cliente no puede crear categorías")
    void customer_cannotWrite() throws Exception {
        mockMvc.perform(post("/api/v1/categories").with(TestJwt.customer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Nueva"}
                                """))
                .andExpect(status().isForbidden());
    }
}
