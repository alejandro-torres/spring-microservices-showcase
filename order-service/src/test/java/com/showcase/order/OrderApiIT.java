package com.showcase.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.showcase.order.application.service.OrderSagaService;
import com.showcase.order.testdata.FakeRemoteServices;
import com.showcase.order.testdata.TestJwt;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End to end: real PostgreSQL, real Feign against a fake HTTP server (product and customer),
 * circuit breaker and forwarding of the user's token. The saga responses are simulated by calling the service
 * (the full path through RabbitMQ is in {@code OrderSagaIT}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderApiIT {

    private static final FakeRemoteServices REMOTE = new FakeRemoteServices();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderSagaService sagaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("showcase.clients.product-service.url", REMOTE::url);
        registry.add("showcase.clients.customer-service.url", REMOTE::url);
    }

    @AfterAll
    static void stopRemote() {
        REMOTE.close();
    }

    private ResultActions as(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.with(TestJwt.admin()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long read(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    @Test
    @DisplayName("walks through the lifecycle of an order, querying customers and products via Feign")
    void fullOrderFlow() throws Exception {
        REMOTE.respond("/api/v1/customers/3", """
                {"id":3,"firstName":"Ana","email":"ana@example.com","addresses":[
                 {"id":1,"street":"Street 1","city":"Lugo","postalCode":"27001","country":"ES","main":false},
                 {"id":2,"street":"Main Street 1","city":"Madrid","postalCode":"28013","country":"ES","main":true}]}
                """);
        REMOTE.respond("/api/v1/products/10", """
                {"id":10,"name":"Mouse","price":12.50,"stock":5,"categoryName":"Peripherals"}""");
        REMOTE.respond("/api/v1/products/11", """
                {"id":11,"name":"Keyboard","price":30.00,"stock":1}""");

        var created = as(post("/api/v1/orders"), """
                {"customerId":3,"items":[{"productId":10,"quantity":2},{"productId":11,"quantity":1}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(55.0))
                .andExpect(jsonPath("$.shippingAddress.city").value("Madrid"))
                .andExpect(jsonPath("$.items[0].productName").value("Mouse"));
        long id = read(created, "$.id");
        long mouseLineId = read(created, "$.items[0].id");
        // The user's token is forwarded to the other services (spring-security-test uses the value "token").
        assertThat(REMOTE.lastAuthorization()).isEqualTo("Bearer token");

        // Business errors with remote data: missing product and insufficient stock.
        as(post("/api/v1/orders"), "{\"customerId\":3,\"items\":[{\"productId\":99,\"quantity\":1}]}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        as(post("/api/v1/orders"), "{\"customerId\":3,\"items\":[{\"productId\":11,\"quantity\":2}]}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        as(post("/api/v1/orders"), "{\"customerId\":4,\"items\":[{\"productId\":10,\"quantity\":1}]}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));

        // PUT only changes the address: the lines (already sent to product-service) are not touched.
        as(put("/api/v1/orders/" + id), """
                {"street":"High Street 5","city":"Sevilla","postalCode":"41001","country":"ES","version":0}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress.city").value("Sevilla"))
                .andExpect(jsonPath("$.total").value(55.0))
                .andExpect(jsonPath("$.items[0].id").value(mouseLineId));
        as(put("/api/v1/orders/" + id), """
                {"street":"High Street 5","city":"Sevilla","postalCode":"41001","country":"ES","version":0}""")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // A pending order has its saga running: it cannot be cancelled.
        mockMvc.perform(put("/api/v1/orders/" + id + "/cancel").with(TestJwt.manager()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));

        // Filters: by product (join with the lines) and the customer's orders.
        mockMvc.perform(get("/api/v1/orders").with(TestJwt.reader()).param("filter", "productId:eq:10")
                        .param("filter", "status:eq:PENDING"))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());
        mockMvc.perform(get("/api/v1/orders/by-customer/3").with(TestJwt.reader()))
                .andExpect(jsonPath("$.content[0].id").value(id));

        // Only closed orders are deleted.
        mockMvc.perform(delete("/api/v1/orders/" + id).with(TestJwt.admin()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ORDER_NOT_DELETABLE"));

        // product-service rejects the reservation: the order stays REJECTED with the reason and can now be deleted.
        sagaService.reject(UUID.randomUUID(), id, "INSUFFICIENT_STOCK");
        mockMvc.perform(get("/api/v1/orders/" + id).with(TestJwt.admin()))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_STOCK"));
        as(put("/api/v1/orders/" + id), """
                {"street":"High Street 5","city":"Sevilla","postalCode":"41001","country":"ES","version":2}""")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ORDER_NOT_MODIFIABLE"));
        mockMvc.perform(delete("/api/v1/orders/" + id).with(TestJwt.admin())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/orders/" + id).with(TestJwt.admin())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("if customer-service fails it responds 503 with no details and the order is not saved")
    void remoteServiceDown_shouldReturn503() throws Exception {
        REMOTE.setDown(true);
        try {
            as(post("/api/v1/orders"), "{\"customerId\":3,\"items\":[{\"productId\":10,\"quantity\":1}]}")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
            mockMvc.perform(get("/api/v1/orders/by-customer/3").with(TestJwt.reader()).param("filter",
                            "status:eq:PENDING").param("filter", "productId:eq:10").param("filter", "total:lte:12.5"))
                    .andExpect(jsonPath("$.totalElements").value(0));
        } finally {
            REMOTE.setDown(false);
        }
    }
}
