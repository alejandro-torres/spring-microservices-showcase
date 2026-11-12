package com.showcase.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.showcase.product.testdata.TestJwt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end test: the full application with real transactions against PostgreSQL.
 * Tokens are simulated with spring-security-test (real JWT validation is tested in auth-service).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductApiIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(TestJwt.admin()));
    }

    private long createdId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long createCategory(String name) throws Exception {
        return createdId(asAdmin(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")).andExpect(status().isCreated()));
    }

    private long createProduct(String name, String price, int stock, long categoryId) throws Exception {
        return createdId(asAdmin(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","price":%s,"stock":%d,"categoryId":%d}
                """.formatted(name, price, stock, categoryId))).andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("walks through the catalog: category, products, filters, stock and deletes with their rules")
    void fullCatalogFlow() throws Exception {
        long monitors = createCategory("Monitors e2e");
        long product = createProduct("Monitor 27 inch", "249.90", 4, monitors);
        createProduct("Monitor 32 inch", "399.00", 0, monitors);

        // Filters: in stock and by category name (join)
        mockMvc.perform(get("/api/v1/products").with(TestJwt.customer())
                        .param("filter", "categoryName:like:monitors e2e").param("filter", "stock:gt:0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Monitor 27 inch"));

        // JPQL query by category, sorted by descending price
        mockMvc.perform(get("/api/v1/categories/{id}/products", monitors).with(TestJwt.customer())
                        .param("sort", "price").param("direction", "desc"))
                .andExpect(jsonPath("$.content[0].name").value("Monitor 32 inch"));

        // Stock adjustments: an incoming one, and an impossible outgoing one that changes nothing
        asAdmin(put("/api/v1/products/{id}/stock", product).contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":6}")).andExpect(jsonPath("$.stock").value(10));
        asAdmin(put("/api/v1/products/{id}/stock", product).contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-11}")).andExpect(status().isUnprocessableContent());
        mockMvc.perform(get("/api/v1/products/{id}", product).with(TestJwt.customer()))
                .andExpect(jsonPath("$.stock").value(10));

        // A category with products cannot be deleted
        asAdmin(delete("/api/v1/categories/{id}", monitors))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_PRODUCTS"));
    }

    @Test
    @DisplayName("optimistic locking: 10 simultaneous adjustments of the same product never leave the stock inconsistent")
    void concurrentStockAdjustments_shouldKeepStockConsistent() throws Exception {
        long category = createCategory("Concurrencia e2e");
        long product = createProduct("Busy product", "10.00", 10, category);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Callable<Integer>> adjustments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            adjustments.add(() -> asAdmin(put("/api/v1/products/{id}/stock", product)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-1}"))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : executor.invokeAll(adjustments)) {
            statuses.add(result.get());
        }
        executor.shutdown();

        long succeeded = statuses.stream().filter(status -> status == 200).count();
        long conflicts = statuses.stream().filter(status -> status == 409).count();
        int finalStock = JsonPath.read(mockMvc.perform(get("/api/v1/products/{id}", product).with(TestJwt.customer()))
                .andReturn().getResponse().getContentAsString(), "$.stock");

        // Each request is either applied or rejected with 409; no adjustment is lost or applied twice.
        assertThat(succeeded + conflicts).isEqualTo(10);
        assertThat(succeeded).isPositive();
        assertThat(finalStock).isEqualTo(10 - (int) succeeded);
    }

    @Test
    @DisplayName("the OpenAPI documentation is public and documents errors with ErrorResponseDTO")
    void openApi_shouldBePublicAndDocumentErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Instance-Id", startsWith("product-service:")))
                .andExpect(jsonPath("$.info.title").value("product-service API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponseDTO").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].get.responses['404'].content"
                        + "['application/json'].schema.$ref").value("#/components/schemas/ErrorResponseDTO"))
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses['409']").exists());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("exposes the metrics for Prometheus with the common tags and the latency histogram")
    void prometheus_shouldExposeTaggedMetrics() throws Exception {
        // given: a request so that there is latency data
        mockMvc.perform(get("/api/v1/products").with(TestJwt.customer())).andExpect(status().isOk());

        // when
        String metrics = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        assertThat(metrics)
                .contains("application=\"product-service\"")
                .contains("environment=\"dev\"")
                .contains("http_server_requests_seconds_bucket")
                .contains("jvm_memory_used_bytes")
                .contains("hikaricp_connections_active");
    }
}
