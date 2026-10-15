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
 * El gateway de verdad (Netty, Spring Security, rate limiting en Redis, circuit breaker) delante de un servidor
 * HTTP falso. Las réplicas se dan con el SimpleDiscoveryClient en lugar de Eureka:
 * <ul>
 *   <li>auth-service y product-service → el servidor falso.</li>
 *   <li>customer-service → un puerto cerrado (servicio caído).</li>
 *   <li>order-service → sin réplicas registradas.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "management.server.port=0",
        "management.tracing.sampling.probability=1.0",
        "RATE_LIMIT_REPLENISH_RATE=1",
        "RATE_LIMIT_BURST_CAPACITY=5",
        "LOGIN_RATE_LIMIT_BURST_CAPACITY=36",
        // El test simula clientes distintos con X-Forwarded-For, como si hubiera un proxy delante.
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

    /** Un usuario distinto por test: el límite de peticiones se cuenta por usuario. */
    private static String newUser() {
        return "user-" + UUID.randomUUID();
    }

    private JsonNode json(EntityExchangeResult<byte[]> result) {
        return jsonMapper.readTree(result.getResponseBody());
    }

    @Nested
    @DisplayName("autenticación")
    class Authentication {

        @Test
        @DisplayName("el registro es público y llega a auth-service sin token")
        void register_withoutToken_shouldBeRouted() {
            // when
            var result = client.post().uri("/api/v1/auth/register").exchange()
                    .expectStatus().isOk().expectBody().returnResult();

            // then
            assertThat(json(result).get("path").asString()).isEqualTo("/api/v1/auth/register");
        }

        @Test
        @DisplayName("sin token responde 401 en el formato común, con el traceId en el cuerpo y en X-Trace-Id")
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
        @DisplayName("con un token válido reenvía la petición con el token y la traza (traceparent)")
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
            // El servicio recibe el host público (para sus redirecciones y URLs), no el suyo interno.
            assertThat(body.get("forwardedHost").asString()).startsWith("localhost:");
        }

        @Test
        @DisplayName("un token firmado con otra clave responde 401 INVALID_TOKEN")
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
    @DisplayName("tokens revocados")
    class Revocation {

        @Test
        @DisplayName("rechaza un token cuyo jti está revocado (logout)")
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
        @DisplayName("rechaza los tokens emitidos antes de revocar los del usuario, pero no los posteriores")
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
        @DisplayName("al superar el límite responde 429 con Retry-After y el mensaje en el idioma pedido")
        void tooManyRequests_shouldReturn429WithRetryAfter() {
            // given: ráfaga de 5 peticiones y 1 por segundo
            String token = TOKENS.token(newUser());
            List<EntityExchangeResult<byte[]>> results = new ArrayList<>();

            // when
            for (int i = 0; i < 8; i++) {
                results.add(client.get().uri("/api/v1/products").headers(h -> {
                    h.setBearerAuth(token);
                    h.set(HttpHeaders.ACCEPT_LANGUAGE, "en");
                }).exchange().expectBody().returnResult());
            }

            // then
            var limited = results.stream()
                    .filter(result -> result.getStatus().value() == HttpStatus.TOO_MANY_REQUESTS.value())
                    .findFirst().orElseThrow(() -> new AssertionError("Ninguna petición ha recibido 429"));
            JsonNode body = json(limited);
            assertThat(limited.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
            assertThat(limited.getResponseHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
            assertThat(body.get("code").asString()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(body.get("message").asString()).startsWith("You have exceeded");
        }
    }

    @Nested
    @DisplayName("rate limiting del login")
    class LoginRateLimiting {

        @Test
        @DisplayName("el login tiene su propio límite por IP, independiente del general, y pide esperar 12 s")
        void login_shouldHaveItsOwnStricterLimit() {
            // given: 12 fichas por intento y capacidad 36 → 3 intentos seguidos; el límite general deja 5
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
    @DisplayName("servicios no disponibles")
    class Unavailable {

        @Test
        @DisplayName("un servicio caído responde 503 del circuit breaker en el formato común")
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
        @DisplayName("un servicio sin réplicas registradas también responde 503")
        void serviceWithoutInstances_shouldReturn503() {
            String token = TOKENS.token(newUser());

            client.get().uri("/api/v1/orders").headers(h -> h.setBearerAuth(token)).exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
        }

        @Test
        @DisplayName("una ruta que no existe responde 404 ENDPOINT_NOT_FOUND")
        void unknownRoute_shouldReturn404() {
            String token = TOKENS.token(newUser());

            client.get().uri("/api/v1/unknown").headers(h -> h.setBearerAuth(token)).exchange()
                    .expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo("ENDPOINT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("enrutado")
    class Routing {

        @Test
        @DisplayName("la DLQ de cada servicio se distingue por un prefijo que el gateway quita")
        void deadLetters_shouldBeRewrittenPerService() {
            String token = TOKENS.token(newUser());

            client.get().uri("/api/v1/admin/product-service/dead-letters").headers(h -> h.setBearerAuth(token))
                    .exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.path").isEqualTo("/api/v1/admin/dead-letters");
        }

        @Test
        @DisplayName("responde las peticiones CORS de comprobación del origen permitido")
        void corsPreflight_fromAllowedOrigin_shouldBeAllowed() {
            client.options().uri("/api/v1/products")
                    .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");
        }

        @Test
        @DisplayName("el Swagger UI agrega la documentación de cada servicio, que es pública")
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
