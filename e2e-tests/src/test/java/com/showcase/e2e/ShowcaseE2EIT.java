package com.showcase.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * De extremo a extremo contra el sistema completo (docker compose), todo a través del gateway:
 * <pre>
 * docker compose up -d --build
 * ./mvnw -Pe2e -pl e2e-tests verify
 * </pre>
 * La URL del gateway se cambia con {@code -De2e.base-url=...}. Usa el usuario {@code admin} de los datos de
 * ejemplo del perfil {@code dev}.
 */
class ShowcaseE2EIT {

    static final String BASE_URL = System.getProperty("e2e.base-url", "http://localhost:8080");
    static final Duration SAGA_TIMEOUT = Duration.ofSeconds(60);

    private static ShowcaseClient admin;

    @BeforeAll
    static void login() throws Exception {
        admin = new ShowcaseClient(BASE_URL);
        admin.login("admin", System.getProperty("e2e.admin-password", "Showcase-Dev-2026!"));
    }

    @Nested
    @DisplayName("pedido de punta a punta")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderFlow {

        @Test
        @Order(1)
        @DisplayName("token de auth-service → producto → cliente → pedido confirmado por la saga → cancelación que "
                + "devuelve el stock")
        void fullOrderSaga() throws Exception {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            // given: un producto con 3 unidades y un cliente con dirección
            var category = admin.post("/api/v1/categories", """
                    {"name":"E2E %s"}""".formatted(suffix));
            assertThat(category.status()).isEqualTo(201);
            var product = admin.post("/api/v1/products", """
                    {"name":"Producto E2E %s","price":19.90,"stock":3,"categoryId":%d}"""
                    .formatted(suffix, category.body().get("id").asLong()));
            assertThat(product.status()).isEqualTo(201);
            long productId = product.body().get("id").asLong();
            var customer = admin.post("/api/v1/customers", """
                    {"firstName":"Cliente","lastName":"E2E","email":"e2e.%s@example.com",
                     "addresses":[{"street":"Gran Vía 1","city":"Madrid","postalCode":"28013","country":"ES"}]}"""
                    .formatted(suffix));
            assertThat(customer.status()).isEqualTo(201);

            // when: se crea un pedido de 2 unidades
            var order = admin.post("/api/v1/orders", """
                    {"customerId":%d,"items":[{"productId":%d,"quantity":2}]}"""
                    .formatted(customer.body().get("id").asLong(), productId));

            // then: nace PENDING, con traza, y la saga lo confirma y descuenta el stock
            assertThat(order.status()).isEqualTo(201);
            assertThat(order.body().get("status").asString()).isEqualTo("PENDING");
            assertThat(order.traceId()).isNotBlank();
            long orderId = order.body().get("id").asLong();
            await().atMost(SAGA_TIMEOUT).pollInterval(Duration.ofSeconds(1)).untilAsserted(() ->
                    assertThat(admin.get("/api/v1/orders/" + orderId).body().get("status").asString())
                            .isEqualTo("CONFIRMED"));
            assertThat(admin.get("/api/v1/products/" + productId).body().get("stock").asInt()).isEqualTo(1);

            // when: se cancela
            var cancelled = admin.put("/api/v1/orders/" + orderId + "/cancel", null);

            // then: la compensación devuelve las 2 unidades
            assertThat(cancelled.status()).isEqualTo(200);
            assertThat(cancelled.body().get("status").asString()).isEqualTo("CANCELLED");
            await().atMost(SAGA_TIMEOUT).pollInterval(Duration.ofSeconds(1)).untilAsserted(() ->
                    assertThat(admin.get("/api/v1/products/" + productId).body().get("stock").asInt())
                            .isEqualTo(3));
        }

        @Test
        @Order(2)
        @DisplayName("un pedido que pide más stock del que hay se rechaza antes de crearlo (422 INSUFFICIENT_STOCK)")
        void orderWithoutStock_shouldBeRejected() throws Exception {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            var category = admin.post("/api/v1/categories", """
                    {"name":"E2E sin stock %s"}""".formatted(suffix));
            long productId = admin.post("/api/v1/products", """
                    {"name":"Sin stock %s","price":5.00,"stock":1,"categoryId":%d}"""
                    .formatted(suffix, category.body().get("id").asLong())).body().get("id").asLong();
            long customerId = admin.post("/api/v1/customers", """
                    {"firstName":"Sin","lastName":"Stock","email":"e2e.nostock.%s@example.com",
                     "addresses":[{"street":"Calle 1","city":"Lugo","postalCode":"27001","country":"ES"}]}"""
                    .formatted(suffix)).body().get("id").asLong();

            var order = admin.post("/api/v1/orders", """
                    {"customerId":%d,"items":[{"productId":%d,"quantity":5}]}""".formatted(customerId, productId));

            assertThat(order.status()).isEqualTo(422);
            assertThat(order.body().get("code").asString()).isEqualTo("INSUFFICIENT_STOCK");
            assertThat(order.body().get("traceId").asString()).isEqualTo(order.traceId());
        }
    }

    @Nested
    @DisplayName("seguridad en el gateway")
    class GatewaySecurity {

        @Test
        @DisplayName("sin token la API responde 401 en el formato común")
        void withoutToken_shouldReturn401() throws Exception {
            var response = new ShowcaseClient(BASE_URL).get("/api/v1/orders");

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.body().get("code").asString()).isEqualTo("UNAUTHORIZED");
        }
    }
}
