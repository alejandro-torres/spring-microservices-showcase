package com.showcase.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.showcase.order.testdata.FakeRemoteServices;
import com.showcase.order.testdata.TestJwt;
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
 * De extremo a extremo: PostgreSQL real, Feign real contra un servidor HTTP falso (product y customer),
 * circuit breaker y reenvío del token del usuario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderApiIT {

    private static final FakeRemoteServices REMOTE = new FakeRemoteServices();

    @Autowired
    private MockMvc mockMvc;

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
    @DisplayName("recorre el ciclo de vida de un pedido consultando clientes y productos por Feign")
    void fullOrderFlow() throws Exception {
        REMOTE.respond("/api/v1/customers/3", """
                {"id":3,"firstName":"Ana","email":"ana@example.com","addresses":[
                 {"id":1,"street":"Calle 1","city":"Lugo","postalCode":"27001","country":"ES","main":false},
                 {"id":2,"street":"Gran Vía 1","city":"Madrid","postalCode":"28013","country":"ES","main":true}]}
                """);
        REMOTE.respond("/api/v1/products/10", """
                {"id":10,"name":"Ratón","price":12.50,"stock":5,"categoryName":"Periféricos"}""");
        REMOTE.respond("/api/v1/products/11", """
                {"id":11,"name":"Teclado","price":30.00,"stock":1}""");

        var created = as(post("/api/v1/orders"), """
                {"customerId":3,"items":[{"productId":10,"quantity":2},{"productId":11,"quantity":1}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(55.0))
                .andExpect(jsonPath("$.shippingAddress.city").value("Madrid"))
                .andExpect(jsonPath("$.items[0].productName").value("Ratón"));
        long id = read(created, "$.id");
        long mouseLineId = read(created, "$.items[0].id");
        // El token del usuario se reenvía a los otros servicios (spring-security-test usa el valor "token").
        assertThat(REMOTE.lastAuthorization()).isEqualTo("Bearer token");

        // Errores de negocio con datos remotos: producto inexistente y stock insuficiente.
        as(post("/api/v1/orders"), "{\"customerId\":3,\"items\":[{\"productId\":99,\"quantity\":1}]}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        as(post("/api/v1/orders"), "{\"customerId\":3,\"items\":[{\"productId\":11,\"quantity\":2}]}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        as(post("/api/v1/orders"), "{\"customerId\":4,\"items\":[{\"productId\":10,\"quantity\":1}]}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));

        // Cambiar las líneas: la del ratón conserva su id, la del teclado se borra.
        as(put("/api/v1/orders/" + id), "{\"items\":[{\"productId\":10,\"quantity\":4}],\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(50.0))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(mouseLineId));
        as(put("/api/v1/orders/" + id), "{\"items\":[{\"productId\":10,\"quantity\":1}],\"version\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // Filtros: por producto (join con las líneas) y pedidos del cliente.
        mockMvc.perform(get("/api/v1/orders").with(TestJwt.reader()).param("filter", "productId:eq:10")
                        .param("filter", "status:eq:PENDING"))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());
        mockMvc.perform(get("/api/v1/orders/by-customer/3").with(TestJwt.reader()))
                .andExpect(jsonPath("$.content[0].id").value(id));

        // Solo se borran pedidos cerrados.
        mockMvc.perform(delete("/api/v1/orders/" + id).with(TestJwt.admin()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ORDER_NOT_DELETABLE"));
        mockMvc.perform(put("/api/v1/orders/" + id + "/cancel").with(TestJwt.manager()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        as(put("/api/v1/orders/" + id), "{\"items\":[{\"productId\":10,\"quantity\":1}],\"version\":2}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ORDER_NOT_MODIFIABLE"));
        mockMvc.perform(delete("/api/v1/orders/" + id).with(TestJwt.admin())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/orders/" + id).with(TestJwt.admin())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("si customer-service falla responde 503 sin detalles y el pedido no se guarda")
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
