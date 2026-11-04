package com.showcase.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.gateway.testsupport.FakeBackend;
import com.showcase.gateway.testsupport.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real gateway (Netty, Spring Security, rate limiting in Redis, circuit breaker) in front of a fake HTTP
 * server. Replicas come from the SimpleDiscoveryClient instead of Eureka:
 * <ul>
 *   <li>auth-service and product-service → the fake server.</li>
 *   <li>customer-service → a closed port (service down).</li>
 *   <li>order-service → no registered replicas.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "management.server.port=0",
        "management.tracing.sampling.probability=1.0",
        "RATE_LIMIT_REPLENISH_RATE=1",
        "RATE_LIMIT_BURST_CAPACITY=5",
        "LOGIN_RATE_LIMIT_BURST_CAPACITY=36",
        // The test simulates different clients with X-Forwarded-For, as if there were a proxy in front.
        "showcase.rate-limit.trusted-proxy-hops=1"})
@Testcontainers
class GatewayIT {

    private static final FakeBackend BACKEND = new FakeBackend();
    private static final TestTokens TOKENS = new TestTokens();

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        BACKEND.respond("/oauth2/jwks", TOKENS.jwksJson());
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> BACKEND.url() + "/oauth2/jwks");
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", BACKEND::url);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", BACKEND::url);
        registry.add("spring.cloud.discovery.client.simple.instances.customer-service[0].uri",
                () -> "http://localhost:1");
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.close();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JsonMapper jsonMapper;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15)).build();
    }

    /** A different user per test: the request limit is counted per user. */
    private static String newUser() {
        return "user-" + UUID.randomUUID();
    }

    private JsonNode json(EntityExchangeResult<byte[]> result) {
        return jsonMapper.readTree(result.getResponseBody());
    }

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @Test
        @DisplayName("registration is public and reaches auth-service without a token")
        void register_withoutToken_shouldBeRouted() {
            // when
            var result = client.post().uri("/api/v1/auth/register").exchange()
                    .expectStatus().isOk().expectBody().returnResult();

            // then
            assertThat(json(result).get("path").asString()).isEqualTo("/api/v1/auth/register");
        }

        @Test
        @DisplayName("uses the strict-origin-when-cross-origin Referrer policy: with no-referrer the browser would send "
                + "Origin: null in the login POST and the CORS filter would reject it")
        void responses_shouldUseReferrerPolicyThatKeepsTheOrigin() {
            // when / then
            client.post().uri("/api/v1/auth/register").exchange()
                    .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
        }

        @Test
        @DisplayName("without a token it responds 401 in the common format, with the traceId in the body and in X-Trace-Id")
        void protectedRoute_withoutToken_shouldReturn401() {
            // when
            var result = client.get().uri("/api/v1/products").exchange()
                    .expectStatus().isUnauthorized().expectBody().returnResult();

            // then
            JsonNode body = json(result);
            assertThat(body.get("code").asString()).isEqualTo("UNAUTHORIZED");
            assertThat(body.get("status").asInt()).isEqualTo(401);
            assertThat(body.get("path").asString()).isEqualTo("/api/v1/products");
            assertThat(body.get("traceId").asString())
                    .isNotBlank()
                    .isEqualTo(result.getResponseHeaders().getFirst("X-Trace-Id"));
        }

        @Test
        @DisplayName("with a valid token it forwards the request with the token and the trace (traceparent)")
        void protectedRoute_withValidToken_shouldForwardTokenAndTrace() {
            // given
            String token = TOKENS.token(newUser());

            // when
            var result = client.get().uri("/api/v1/products/7").headers(h -> h.setBearerAuth(token)).exchange()
                    .expectStatus().isOk().expectBody().returnResult();

            // then
            JsonNode body = json(result);
            String traceId = result.getResponseHeaders().getFirst("X-Trace-Id");
            assertThat(body.get("path").asString()).isEqualTo("/api/v1/products/7");
            assertThat(body.get("authorization").asString()).isEqualTo("Bearer " + token);
            assertThat(traceId).isNotBlank();
            assertThat(body.get("traceparent").asString()).contains(traceId);
            // The service receives the public host (for its redirects and URLs), not its internal one.
            assertThat(body.get("forwardedHost").asString()).startsWith("localhost:");
        }

        @Test
        @DisplayName("a token signed with another key responds 401 INVALID_TOKEN")
        void protectedRoute_withForeignToken_shouldReturn401InvalidToken() {
            // given
            String foreignToken = new TestTokens().token(newUser());

            // when
            var result = client.get().uri("/api/v1/products").headers(h -> h.setBearerAuth(foreignToken))
                    .exchange().expectStatus().isUnauthorized().expectBody().returnResult();

            // then
            assertThat(json(result).get("code").asString()).isEqualTo("INVALID_TOKEN");
        }
    }

    @Nested
    @DisplayName("revoked tokens")
    class Revocation {

        @Test
        @DisplayName("rejects a token whose jti is revoked (logout)")
        void revokedJti_shouldReturn401() {
            // given
            String jti = UUID.randomUUID().toString();
            String token = TOKENS.token(newUser(), jti, Instant.now());
            redis.opsForValue().set("auth:revoked:token:" + jti, "1", Duration.ofMinutes(5));

            // when
            var result = client.get().uri("/api/v1/products").headers(h -> h.setBearerAuth(token)).exchange()
                    .expectStatus().isUnauthorized().expectBody().returnResult();

            // then
            assertThat(json(result).get("code").asString()).isEqualTo("INVALID_TOKEN");
        }

        @Test
        @DisplayName("rejects tokens issued before revoking the user's, but not later ones")
        void userRevocation_shouldOnlyAffectOlderTokens() {
            // given
            String user = newUser();
            Instant revokedAt = Instant.now().minusSeconds(60);
            redis.opsForValue().set("auth:revoked:user:" + user, String.valueOf(revokedAt.getEpochSecond()),
                    Duration.ofMinutes(5));
            String oldToken = TOKENS.token(user, UUID.randomUUID().toString(), revokedAt.minusSeconds(30));
            String newToken = TOKENS.token(user, UUID.randomUUID().toString(), revokedAt.plusSeconds(30));

            // when / then
            client.get().uri("/api/v1/products").headers(h -> h.setBearerAuth(oldToken)).exchange()
                    .expectStatus().isUnauthorized();
            client.get().uri("/api/v1/products").headers(h -> h.setBearerAuth(newToken)).exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("rate limiting")
    class RateLimiting {

        @Test
        @DisplayName("on exceeding the limit it responds 429 with Retry-After and the message in the requested language")
        void tooManyRequests_shouldReturn429WithRetryAfter() {
            // given: a burst of 5 requests and 1 per second
            String token = TOKENS.token(newUser());
            List<EntityExchangeResult<byte[]>> results = new ArrayList<>();

            // when
            for (int i = 0; i < 8; i++) {
                results.add(client.get().uri("/api/v1/products").headers(h -> {
                    h.setBearerAuth(token);
                    h.set(HttpHeaders.ACCEPT_LANGUAGE, "es");
                }).exchange().expectBody().returnResult());
            }

            // then
            var limited = results.stream()
                    .filter(result -> result.getStatus().value() == HttpStatus.TOO_MANY_REQUESTS.value())
                    .findFirst().orElseThrow(() -> new AssertionError("No request received a 429"));
            JsonNode body = json(limited);
            assertThat(limited.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
            assertThat(limited.getResponseHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
            assertThat(body.get("code").asString()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(body.get("message").asString()).startsWith("Has superado");
        }
    }

    @Nested
    @DisplayName("login rate limiting")
    class LoginRateLimiting {

        @Test
        @DisplayName("the login has its own per-IP limit, independent of the general one, and asks to wait 12 s")
        void login_shouldHaveItsOwnStricterLimit() {
            // given: 12 tokens per attempt and capacity 36 → 3 attempts in a row; the general limit allows 5
            String clientIp = "203.0.113." + (int) (Math.random() * 200 + 1);
            List<Integer> statuses = new ArrayList<>();
            EntityExchangeResult<byte[]> last = null;

            // when
            for (int i = 0; i < 4; i++) {
                last = client.post().uri("/login").header("X-Forwarded-For", clientIp).exchange()
                        .expectBody().returnResult();
                statuses.add(last.getStatus().value());
            }

            // then
            assertThat(statuses).containsExactly(200, 200, 200, 429);
            assertThat(last.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("12");
            assertThat(json(last).get("code").asString()).isEqualTo("RATE_LIMIT_EXCEEDED");
        }
    }

    @Nested
    @DisplayName("unavailable services")
    class Unavailable {

        @Test
        @DisplayName("a downed service responds 503 from the circuit breaker in the common format")
        void serviceDown_shouldReturn503() {
            // given
            String token = TOKENS.token(newUser());

            // when
            var result = client.get().uri("/api/v1/customers").headers(h -> h.setBearerAuth(token)).exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE).expectBody().returnResult();

            // then
            var body = json(result);
            assertThat(body.get("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(body.get("path").asString()).isEqualTo("/api/v1/customers");
        }

        @Test
        @DisplayName("a service with no registered replicas also responds 503")
        void serviceWithoutInstances_shouldReturn503() {
            String token = TOKENS.token(newUser());

            client.get().uri("/api/v1/orders").headers(h -> h.setBearerAuth(token)).exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
        }

        @Test
        @DisplayName("a path that does not exist responds 404 ENDPOINT_NOT_FOUND")
        void unknownRoute_shouldReturn404() {
            String token = TOKENS.token(newUser());

            client.get().uri("/api/v1/unknown").headers(h -> h.setBearerAuth(token)).exchange()
                    .expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo("ENDPOINT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("routing")
    class Routing {

        @Test
        @DisplayName("each service's DLQ is told apart by a prefix the gateway strips")
        void deadLetters_shouldBeRewrittenPerService() {
            String token = TOKENS.token(newUser());

            client.get().uri("/api/v1/admin/product-service/dead-letters").headers(h -> h.setBearerAuth(token))
                    .exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.path").isEqualTo("/api/v1/admin/dead-letters");
        }

        @Test
        @DisplayName("answers CORS preflight requests from the allowed origin")
        void corsPreflight_fromAllowedOrigin_shouldBeAllowed() {
            client.options().uri("/api/v1/products")
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
        }

        @Test
        @DisplayName("the Swagger UI aggregates each service's documentation, which is public")
        void swagger_shouldAggregateServiceDocs() {
            client.get().uri("/v3/api-docs/swagger-config").exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.urls[?(@.name == 'product-service')].url")
                    .isEqualTo("/product-service/v3/api-docs");
            client.get().uri("/product-service/v3/api-docs").exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.path").isEqualTo("/v3/api-docs");
        }
    }
}
