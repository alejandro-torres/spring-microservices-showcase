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
 * End to end against the full system (docker compose), everything through the gateway:
 * <pre>
 * docker compose up -d --build
 * ./mvnw -Pe2e -pl e2e-tests verify
 * </pre>
 * The gateway URL is changed with {@code -De2e.base-url=...}. It uses the {@code admin} user from the sample
 * data of the {@code dev} profile.
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
    @DisplayName("order from end to end")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderFlow {

        @Test
        @Order(1)
        @DisplayName("auth-service token → product → customer → order confirmed by the saga → cancellation that "
                + "returns the stock")
        void fullOrderSaga() throws Exception {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            // given: a product with 3 units and a customer with an address
            var category = admin.post("/api/v1/categories", """
                    {"name":"E2E %s"}""".formatted(suffix));
            assertThat(category.status()).isEqualTo(201);
            var product = admin.post("/api/v1/products", """
                    {"name":"Product E2E %s","price":19.90,"stock":3,"categoryId":%d}"""
                    .formatted(suffix, category.body().get("id").asLong()));
            assertThat(product.status()).isEqualTo(201);
            long productId = product.body().get("id").asLong();
            var customer = admin.post("/api/v1/customers", """
                    {"firstName":"Customer","lastName":"E2E","email":"e2e.%s@example.com",
                     "addresses":[{"street":"Main Street 1","city":"Madrid","postalCode":"28013","country":"ES"}]}"""
                    .formatted(suffix));
            assertThat(customer.status()).isEqualTo(201);

            // when: an order of 2 units is created
            var order = admin.post("/api/v1/orders", """
                    {"customerId":%d,"items":[{"productId":%d,"quantity":2}]}"""
                    .formatted(customer.body().get("id").asLong(), productId));

            // then: it starts PENDING, with a trace, and the saga confirms it and deducts the stock
            assertThat(order.status()).isEqualTo(201);
            assertThat(order.body().get("status").asString()).isEqualTo("PENDING");
            assertThat(order.traceId()).isNotBlank();
            long orderId = order.body().get("id").asLong();
            await().atMost(SAGA_TIMEOUT).pollInterval(Duration.ofSeconds(1)).untilAsserted(() ->
                    assertThat(admin.get("/api/v1/orders/" + orderId).body().get("status").asString())
                            .isEqualTo("CONFIRMED"));
            assertThat(admin.get("/api/v1/products/" + productId).body().get("stock").asInt()).isEqualTo(1);

            // when: it is cancelled
            var cancelled = admin.put("/api/v1/orders/" + orderId + "/cancel", null);

            // then: the compensation returns the 2 units
            assertThat(cancelled.status()).isEqualTo(200);
            assertThat(cancelled.body().get("status").asString()).isEqualTo("CANCELLED");
            await().atMost(SAGA_TIMEOUT).pollInterval(Duration.ofSeconds(1)).untilAsserted(() ->
                    assertThat(admin.get("/api/v1/products/" + productId).body().get("stock").asInt())
                            .isEqualTo(3));
        }

        @Test
        @Order(2)
        @DisplayName("an order requesting more stock than available is rejected before creating it (422 INSUFFICIENT_STOCK)")
        void orderWithoutStock_shouldBeRejected() throws Exception {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            var category = admin.post("/api/v1/categories", """
                    {"name":"E2E out of stock %s"}""".formatted(suffix));
            long productId = admin.post("/api/v1/products", """
                    {"name":"Out of stock %s","price":5.00,"stock":1,"categoryId":%d}"""
                    .formatted(suffix, category.body().get("id").asLong())).body().get("id").asLong();
            long customerId = admin.post("/api/v1/customers", """
                    {"firstName":"No","lastName":"Stock","email":"e2e.nostock.%s@example.com",
                     "addresses":[{"street":"Street 1","city":"Lugo","postalCode":"27001","country":"ES"}]}"""
                    .formatted(suffix)).body().get("id").asLong();

            var order = admin.post("/api/v1/orders", """
                    {"customerId":%d,"items":[{"productId":%d,"quantity":5}]}""".formatted(customerId, productId));

            assertThat(order.status()).isEqualTo(422);
            assertThat(order.body().get("code").asString()).isEqualTo("INSUFFICIENT_STOCK");
            assertThat(order.body().get("traceId").asString()).isEqualTo(order.traceId());
        }
    }

    @Nested
    @DisplayName("gateway security")
    class GatewaySecurity {

        @Test
        @DisplayName("without a token the API responds 401 in the common format")
        void withoutToken_shouldReturn401() throws Exception {
            var response = new ShowcaseClient(BASE_URL).get("/api/v1/orders");

            assertThat(response.status()).isEqualTo(401);
            assertThat(response.body().get("code").asString()).isEqualTo("UNAUTHORIZED");
        }
    }
}
