package com.showcase.product.application.service.impl;

import static com.showcase.product.testdata.ProductMother.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.product.application.lock.DistributedLockManager;
import com.showcase.product.application.metrics.StockMetrics;
import com.showcase.product.application.service.StockLine;
import com.showcase.product.domain.event.StockReleasedEvent;
import com.showcase.product.domain.event.StockReservationFailedEvent;
import com.showcase.product.domain.event.StockReservedEvent;
import com.showcase.product.domain.messaging.StockEventPublisher;
import com.showcase.product.domain.model.Product;
import com.showcase.product.domain.repository.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final List<StockLine> LINES = List.of(new StockLine(11L, 1), new StockLine(10L, 2));

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProcessedEventRegistry processedEvents;

    @Mock
    private StockEventPublisher eventPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private StockMetrics metrics;

    /** A lock that is always acquired and notes which keys were requested. */
    private final List<String> lockedKeys = new ArrayList<>();
    private final DistributedLockManager lockManager = new DistributedLockManager() {
        @Override
        public <T> T executeWithLocks(Collection<String> keys, Supplier<T> action) {
            lockedKeys.addAll(keys);
            return action.get();
        }
    };

    private StockServiceImpl stockService;

    private Product mouse;
    private Product keyboard;

    @BeforeEach
    void setUp() {
        stockService = new StockServiceImpl(productRepository, processedEvents, eventPublisher, lockManager, metrics,
                new TransactionTemplate(transactionManager), new TransactionTemplate(transactionManager),
                Clock.fixed(NOW, ZoneOffset.UTC));
        mouse = product(10L, "Mouse", "5.00", 5);
        keyboard = product(11L, "Keyboard", "20.00", 1);
    }

    @Nested
    @DisplayName("distributed lock")
    class DistributedLock {

        @Test
        @DisplayName("requests one lock per product and opens the transaction after acquiring them")
        void reserve_shouldLockEveryProductBeforeTransaction() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "order.created")).willReturn(false);

            // when
            stockService.reserve(EVENT_ID, 7L, LINES);

            // then
            assertThat(lockedKeys).containsExactlyInAnyOrder("lock:product:10", "lock:product:11");
            verify(transactionManager).getTransaction(any());
        }

        @Test
        @DisplayName("if it does not get the lock it neither opens the transaction nor touches the stock (the message will be retried)")
        void reserve_whenLockBusy_shouldNotTouchStock() {
            // given
            var busy = new BusinessRuleException(CommonErrorCode.RESOURCE_BUSY, "lock:product:10");
            var busyLockManager = new DistributedLockManager() {
                @Override
                public <T> T executeWithLocks(Collection<String> keys, Supplier<T> action) {
                    throw busy;
                }
            };
            var service = new StockServiceImpl(productRepository, processedEvents, eventPublisher, busyLockManager,
                    metrics, new TransactionTemplate(transactionManager),
                    new TransactionTemplate(transactionManager), Clock.fixed(NOW, ZoneOffset.UTC));

            // when / then
            assertThatThrownBy(() -> service.reserve(EVENT_ID, 7L, LINES)).isSameAs(busy);
            verifyNoInteractions(transactionManager, productRepository, processedEvents, eventPublisher);
        }

        @Test
        @DisplayName("one key per product even if it appears in several lines")
        void lockKeys_shouldBeDistinctPerProduct() {
            assertThat(StockServiceImpl.lockKeys(List.of(new StockLine(5L, 1), new StockLine(5L, 2))))
                    .containsExactly("lock:product:5");
        }
    }

    @Nested
    @DisplayName("reserve")
    class Reserve {

        @Test
        @DisplayName("with stock for all lines, it deducts each one and publishes stock.reserved")
        void reserve_withStock_shouldReserveAllLines() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "order.created")).willReturn(true);
            given(productRepository.findById(10L)).willReturn(Optional.of(mouse));
            given(productRepository.findById(11L)).willReturn(Optional.of(keyboard));

            // when
            stockService.reserve(EVENT_ID, 7L, LINES);

            // then
            assertThat(mouse.getStock()).isEqualTo(3);
            assertThat(keyboard.getStock()).isZero();
            verify(productRepository, times(2)).save(any(Product.class));
            verify(eventPublisher).publish(new StockReservedEvent(7L, NOW));
            verify(metrics).stockReserved();
        }

        @Test
        @DisplayName("if a line lacks stock none is deducted and it publishes INSUFFICIENT_STOCK")
        void reserve_withoutStock_shouldReserveNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "order.created")).willReturn(true);
            given(productRepository.findById(10L)).willReturn(Optional.of(mouse));
            given(productRepository.findById(11L)).willReturn(Optional.of(keyboard));

            // when
            stockService.reserve(EVENT_ID, 7L, List.of(new StockLine(10L, 2), new StockLine(11L, 3)));

            // then
            assertThat(mouse.getStock()).isEqualTo(5);
            verify(productRepository, never()).save(any());
            verify(eventPublisher).publish(new StockReservationFailedEvent(7L, "INSUFFICIENT_STOCK", 11L, NOW));
            verify(metrics).reservationFailed("INSUFFICIENT_STOCK");
        }

        @Test
        @DisplayName("if a product does not exist it publishes PRODUCT_NOT_FOUND without touching the stock")
        void reserve_withUnknownProduct_shouldFail() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "order.created")).willReturn(true);
            given(productRepository.findById(10L)).willReturn(Optional.empty());

            // when
            stockService.reserve(EVENT_ID, 7L, LINES);

            // then
            verify(productRepository, never()).save(any());
            verify(eventPublisher).publish(new StockReservationFailedEvent(7L, "PRODUCT_NOT_FOUND", 10L, NOW));
        }

        @Test
        @DisplayName("processes the products sorted by id to always lock them in the same order")
        void reserve_shouldProcessProductsInIdOrder() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "order.created")).willReturn(true);
            given(productRepository.findById(10L)).willReturn(Optional.of(mouse));
            given(productRepository.findById(11L)).willReturn(Optional.of(keyboard));

            // when
            stockService.reserve(EVENT_ID, 7L, LINES);

            // then
            var captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues()).extracting(Product::getId).containsExactly(10L, 11L);
        }

        @Test
        @DisplayName("ignores a duplicate order.created")
        void reserve_duplicate_shouldDoNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "order.created")).willReturn(false);

            // when
            stockService.reserve(EVENT_ID, 7L, LINES);

            // then
            verifyNoInteractions(productRepository, eventPublisher);
        }
    }

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        @DisplayName("returns the units of each line, even if the product is deleted, and publishes stock.released")
        void release_shouldReturnStock() {
            // given
            mouse.deactivate();
            given(processedEvents.registerIfAbsent(EVENT_ID, "order.cancelled")).willReturn(true);
            given(productRepository.findByIdIncludingInactive(10L)).willReturn(Optional.of(mouse));
            given(productRepository.findByIdIncludingInactive(11L)).willReturn(Optional.of(keyboard));

            // when
            stockService.release(EVENT_ID, 7L, LINES);

            // then
            assertThat(mouse.getStock()).isEqualTo(7);
            assertThat(keyboard.getStock()).isEqualTo(2);
            verify(eventPublisher).publish(new StockReleasedEvent(7L, NOW));
            verify(metrics).stockReleased();
        }

        @Test
        @DisplayName("if a product no longer exists, it returns the rest and publishes stock.released anyway")
        void release_withMissingProduct_shouldSkipIt() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "order.cancelled")).willReturn(true);
            given(productRepository.findByIdIncludingInactive(10L)).willReturn(Optional.empty());
            given(productRepository.findByIdIncludingInactive(11L)).willReturn(Optional.of(keyboard));

            // when
            stockService.release(EVENT_ID, 7L, LINES);

            // then
            verify(productRepository).save(keyboard);
            verify(eventPublisher).publish(new StockReleasedEvent(7L, NOW));
            verify(metrics).stockReleased();
        }

        @Test
        @DisplayName("ignores a duplicate order.cancelled: the stock is not returned twice")
        void release_duplicate_shouldDoNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "order.cancelled")).willReturn(false);

            // when
            stockService.release(EVENT_ID, 7L, LINES);

            // then
            verifyNoInteractions(productRepository, eventPublisher);
        }
    }
}
