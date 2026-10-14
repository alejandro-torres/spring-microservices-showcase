package com.showcase.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.DomainException;
import com.showcase.product.application.service.CategoryService;
import com.showcase.product.application.service.ProductService;
import com.showcase.product.application.service.StockLine;
import com.showcase.product.application.service.StockService;
import com.showcase.product.infrastructure.lock.impl.RedissonDistributedLockManagerImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Concurrencia con PostgreSQL y Redis reales. Varias réplicas se simulan con varios hilos: todos pasan por el mismo
 * lock de Redis, igual que lo harían procesos distintos.
 */
@SpringBootTest(properties = "showcase.messaging.outbox.enabled=false")
@Import(TestcontainersConfiguration.class)
class StockConcurrencyIT {

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Long createProduct(int stock) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long categoryId = categoryService.create("Concurrencia " + suffix, null).getId();
        return productService.create("Último stock " + suffix, null, new BigDecimal("9.99"), stock, categoryId)
                .getId();
    }

    @Nested
    @DisplayName("reservas simultáneas")
    class ConcurrentReservations {

        @Test
        @DisplayName("20 pedidos a la vez sobre las 5 últimas unidades: se reservan exactamente 5 y el stock no "
                + "queda negativo")
        void concurrentReservations_onLastUnits_shouldNeverOversell() throws Exception {
            // given
            Long productId = createProduct(5);
            int orders = 20;
            ExecutorService pool = Executors.newFixedThreadPool(orders);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> results = new ArrayList<>();

            // when: todos empiezan a la vez
            for (int i = 0; i < orders; i++) {
                long orderId = 90_000L + i;
                results.add(pool.submit(() -> {
                    start.await();
                    stockService.reserve(UUID.randomUUID(), orderId, List.of(new StockLine(productId, 1)));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
            pool.shutdown();

            // then
            assertThat(productService.findById(productId).getStock()).isZero();
            assertThat(countReplies("stock.reserved", productId)).isEqualTo(5);
            assertThat(countReplies("stock.reservation-failed", productId)).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("lock ocupado")
    class BusyLock {

        @Test
        @DisplayName("si otra réplica tiene el lock del producto, se rinde a tiempo con RESOURCE_BUSY")
        void lockHeldByOtherReplica_shouldFailWithResourceBusy() throws Exception {
            // given: otra "réplica" coge el lock y lo retiene
            var otherReplica = new RedissonDistributedLockManagerImpl(redisson, Duration.ofSeconds(1),
                    Duration.ofSeconds(10), new SimpleMeterRegistry());
            var impatient = new RedissonDistributedLockManagerImpl(redisson, Duration.ofMillis(200),
                    Duration.ofSeconds(10), new SimpleMeterRegistry());
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<?> holder = pool.submit(() -> otherReplica.executeWithLocks(List.of("lock:product:1"), () -> {
                locked.countDown();
                await(release);
                return null;
            }));
            locked.await(5, TimeUnit.SECONDS);

            // when / then
            assertThatThrownBy(() -> impatient.executeWithLocks(List.of("lock:product:1", "lock:product:2"),
                    () -> "no debería ejecutarse"))
                    .isInstanceOf(DomainException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.RESOURCE_BUSY);

            // and: al soltarlo, el lock vuelve a estar disponible (y el segundo, que no llegó a cogerse, también)
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(impatient.executeWithLocks(List.of("lock:product:1", "lock:product:2"), () -> "ok"))
                    .isEqualTo("ok");
        }
    }

    @Nested
    @DisplayName("lock tras un fallo")
    class LockAfterFailure {

        @Test
        @DisplayName("si la operación falla, el lock se suelta igualmente y otra réplica puede cogerlo")
        void failingAction_shouldReleaseLock() {
            // given
            var replica = new RedissonDistributedLockManagerImpl(redisson, Duration.ofMillis(200),
                    Duration.ofSeconds(10), new SimpleMeterRegistry());
            var otherReplica = new RedissonDistributedLockManagerImpl(redisson, Duration.ofMillis(200),
                    Duration.ofSeconds(10), new SimpleMeterRegistry());

            // when
            assertThatThrownBy(() -> replica.executeWithLocks(List.of("lock:product:77"), () -> {
                throw new IllegalStateException("fallo en mitad de la reserva");
            })).isInstanceOf(IllegalStateException.class);

            // then: desde otro hilo (otra "réplica") se consigue enseguida, sin esperar a que caduque
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                assertThat(pool.submit(() -> otherReplica.executeWithLocks(List.of("lock:product:77"), () -> "ok"))
                        .get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                pool.shutdown();
            }
        }
    }

    private long countReplies(String eventType, Long productId) {
        return jdbcClient.sql("""
                SELECT count(*) FROM outbox_event
                WHERE event_type = :type AND CAST(aggregate_id AS BIGINT) BETWEEN 90000 AND 90019
                  AND (:type <> 'stock.reservation-failed' OR payload -> 'payload' ->> 'productId' = :productId)
                """)
                .param("type", eventType)
                .param("productId", String.valueOf(productId))
                .query(Long.class).single();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
