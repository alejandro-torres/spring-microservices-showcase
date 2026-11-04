package com.showcase.product.infrastructure.rest.controller;

import static com.showcase.product.testdata.ProductMother.laptop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.filter.FilterOperator;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.config.CommonWebAutoConfiguration;
import com.showcase.product.application.service.ProductService;
import com.showcase.product.domain.exception.InsufficientStockException;
import com.showcase.product.domain.exception.ProductNotFoundException;
import com.showcase.product.infrastructure.rest.mapper.ProductRestMapperImpl;
import com.showcase.product.infrastructure.security.SecurityConfig;
import com.showcase.product.testdata.TestJwt;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({CommonWebAutoConfiguration.class, SecurityConfig.class, ProductRestMapperImpl.class})
class ProductControllerTest {

    private static final String CREATE_BODY = """
            {"name":"Laptop Air 13","description":"Light","price":999.00,"stock":3,"categoryId":1}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Nested
    @DisplayName("POST and GET")
    class CreateAndRead {

        @Test
        @DisplayName("POST returns 201 with the location and the product's category")
        void create_shouldReturn201() throws Exception {
            given(productService.create("Laptop Air 13", "Light", new BigDecimal("999.00"), 3, 1L))
                    .willReturn(laptop());

            mockMvc.perform(post("/api/v1/products").with(TestJwt.catalogManager())
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/products/10")))
                    .andExpect(jsonPath("$.categoryId").value(1))
                    .andExpect(jsonPath("$.categoryName").value("Laptops"))
                    .andExpect(jsonPath("$.price").value(1499.00));
        }

        @Test
        @DisplayName("POST validates price, stock and category")
        void create_withInvalidBody_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/products").with(TestJwt.catalogManager())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"X","price":10.999,"stock":-1}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "price", "stock", "categoryId")));
        }

        @Test
        @DisplayName("GET by id returns a translated 404 if the product does not exist")
        void findById_missing_shouldReturn404() throws Exception {
            given(productService.findById(99L)).willThrow(new ProductNotFoundException(99L));

            mockMvc.perform(get("/api/v1/products/99").with(TestJwt.customer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No active product with id 99 was found."));
        }
    }

    @Nested
    @DisplayName("Paginated GET with filters")
    class FindAll {

        @Test
        @DisplayName("converts the filters (including in and between with commas) and the sort")
        void findAll_shouldParseFilters() throws Exception {
            given(productService.findAll(anyList(), any(PageQuery.class), anyBoolean()))
                    .willReturn(PageResult.of(List.of(laptop()), 0, 10, 1));

            mockMvc.perform(get("/api/v1/products").with(TestJwt.customer())
                            .param("filter", "price:between:100,1500")
                            .param("filter", "categoryId:in:1,2")
                            .param("sort", "price").param("direction", "desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Laptop Pro 15"))
                    .andExpect(jsonPath("$.totalElements").value(1));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FilterCriterion>> filters = ArgumentCaptor.forClass(List.class);
            verify(productService).findAll(filters.capture(), eq(new PageQuery(0, 10, "price", SortDirection.DESC)),
                    eq(false));
            assertThat(filters.getValue()).containsExactly(
                    FilterCriterion.of("price", FilterOperator.BETWEEN, new BigDecimal("100"), new BigDecimal("1500")),
                    FilterCriterion.of("categoryId", FilterOperator.IN, 1L, 2L));
        }

        @Test
        @DisplayName("rejects an operator not allowed and an includeInactive without the ADMIN role")
        void findAll_invalidRequests_shouldBeRejected() throws Exception {
            mockMvc.perform(get("/api/v1/products").with(TestJwt.customer()).param("filter", "price:like:10"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("FILTER_OPERATOR_NOT_ALLOWED"));
            mockMvc.perform(get("/api/v1/products").with(TestJwt.customer()).param("includeInactive", "true"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("permissions, modification and stock")
    class WriteOperations {

        @Test
        @DisplayName("without a token it returns 401 and a customer (product:read only) cannot create")
        void permissions_shouldBeEnforced() throws Exception {
            mockMvc.perform(get("/api/v1/products/10")).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/products").with(TestJwt.customer())
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isForbidden());
            verify(productService, never()).create(any(), any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("PUT updates with the version and DELETE returns 204")
        void updateAndDelete_shouldWork() throws Exception {
            given(productService.update(10L, "Laptop Pro 16", null, new BigDecimal("1599.99"), 1L, 0L))
                    .willReturn(laptop());

            mockMvc.perform(put("/api/v1/products/10").with(TestJwt.catalogManager())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Laptop Pro 16","price":1599.99,"categoryId":1,"version":0}
                                    """))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/v1/products/10").with(TestJwt.catalogManager()))
                    .andExpect(status().isNoContent());
            verify(productService).delete(10L);
        }

        @Test
        @DisplayName("the stock adjustment is ADMIN only and an impossible adjustment returns 422")
        void adjustStock_onlyAdmin() throws Exception {
            given(productService.adjustStock(10L, -50)).willThrow(new InsufficientStockException("Laptop Pro 15", 5, 50));
            String body = """
                    {"delta":-50}
                    """;

            mockMvc.perform(put("/api/v1/products/10/stock").with(TestJwt.catalogManager())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/products/10/stock").with(TestJwt.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                    .andExpect(jsonPath("$.message")
                            .value("Not enough stock of 'Laptop Pro 15': 5 available, 50 requested."));
        }
    }
}
