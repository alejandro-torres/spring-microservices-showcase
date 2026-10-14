package com.showcase.product.application.service.impl;

import static com.showcase.product.testdata.ProductMother.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.showcase.common.domain.messaging.ProcessedEventRegistry;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private StockServiceImpl stockService;

    private Product mouse;
    private Product keyboard;

    @BeforeEach
    void setUp() {
        stockService = new StockServiceImpl(productRepository, processedEvents, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        mouse = product(10L, "Ratón", "5.00", 5);
        keyboard = product(11L, "Teclado", "20.00", 1);
    }

    @Nested
    @DisplayName("reserve")
    class Reserve {

        @Test
        @DisplayName("con stock de todas las líneas, descuenta cada una y publica stock.reserved")
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
        }

        @Test
        @DisplayName("si falta stock en una línea no descuenta ninguna y publica INSUFFICIENT_STOCK")
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
        }

        @Test
        @DisplayName("si un producto no existe publica PRODUCT_NOT_FOUND sin tocar el stock")
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
        @DisplayName("procesa los productos ordenados por id para bloquearlos siempre en el mismo orden")
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
        @DisplayName("ignora un order.created duplicado")
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
        @DisplayName("devuelve las unidades de cada línea, aunque el producto esté borrado, y publica stock.released")
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
        }

        @Test
        @DisplayName("si un producto ya no existe, devuelve el resto y publica stock.released igualmente")
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
        }

        @Test
        @DisplayName("ignora un order.cancelled duplicado: el stock no se devuelve dos veces")
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
