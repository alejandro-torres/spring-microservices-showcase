package com.showcase.product.application.service.impl;

import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.events.EventTypes;
import com.showcase.product.application.service.StockLine;
import com.showcase.product.application.service.StockService;
import com.showcase.product.domain.event.StockReleasedEvent;
import com.showcase.product.domain.event.StockReservationFailedEvent;
import com.showcase.product.domain.event.StockReservedEvent;
import com.showcase.product.domain.exception.ProductErrorCode;
import com.showcase.product.domain.messaging.StockEventPublisher;
import com.showcase.product.domain.model.Product;
import com.showcase.product.domain.repository.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lado de product-service de la saga de pedidos. Cada operación registra el evento procesado, cambia el stock y
 * guarda su evento de respuesta en el outbox en UNA transacción: o pasa todo o nada.
 * <p>
 * Que falte stock no es un error técnico sino un resultado de negocio: no se lanza ninguna excepción, se publica
 * {@code stock.reservation-failed} y la transacción hace commit (con el evento procesado registrado, para no
 * repetir la respuesta si el mensaje llega dos veces).
 */
@Service
public class StockServiceImpl implements StockService {

    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    private final ProductRepository productRepository;
    private final ProcessedEventRegistry processedEvents;
    private final StockEventPublisher eventPublisher;
    private final Clock clock;

    public StockServiceImpl(ProductRepository productRepository, ProcessedEventRegistry processedEvents,
                            StockEventPublisher eventPublisher, Clock clock) {
        this.productRepository = productRepository;
        this.processedEvents = processedEvents;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Todas las líneas o ninguna: primero se comprueba que todas tienen stock y solo entonces se descuentan.
     * <p>
     * Concurrencia, de menos a más protección:
     * <ul>
     *   <li>{@code @Version} (bloqueo optimista): si otra transacción cambió el producto entretanto, el UPDATE
     *       falla y el mensaje se reintenta con datos frescos.</li>
     *   <li>{@code REPEATABLE_READ}: en PostgreSQL, si otra transacción ya confirmada modificó una fila que esta
     *       va a actualizar, la actualización falla con un error de serialización (también se reintenta). Así se
     *       detecta el conflicto aunque una ruta de escritura no pasara por la versión de la entidad.</li>
     *   <li>Lock distribuido por producto con Redisson (fase de escalado horizontal): evita los conflictos en
     *       lugar de detectarlos, para que con varias réplicas no haya reintentos en cadena.</li>
     * </ul>
     * Los productos se procesan ordenados por id para que dos reservas simultáneas los bloqueen en el mismo
     * orden y no se produzcan interbloqueos en la base de datos.
     */
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public void reserve(UUID eventId, Long orderId, List<StockLine> lines) {
        if (!processedEvents.registerIfAbsent(eventId, EventTypes.ORDER_CREATED)) {
            log.info("Evento {} (order.created) ya procesado; se ignora el duplicado", eventId);
            return;
        }
        Instant now = Instant.now(clock);
        List<Reservation> reservations = new ArrayList<>();
        for (StockLine line : sortedByProduct(lines)) {
            Optional<Product> product = productRepository.findById(line.productId());
            if (product.isEmpty()) {
                fail(orderId, ProductErrorCode.PRODUCT_NOT_FOUND, line.productId(), now);
                return;
            }
            if (!product.get().hasStock(line.quantity())) {
                fail(orderId, ProductErrorCode.INSUFFICIENT_STOCK, line.productId(), now);
                return;
            }
            reservations.add(new Reservation(product.get(), line.quantity()));
        }
        for (Reservation reservation : reservations) {
            reservation.product().reserveStock(reservation.quantity());
            productRepository.save(reservation.product());
        }
        eventPublisher.publish(new StockReservedEvent(orderId, now));
        log.info("Stock reservado para el pedido {} ({} líneas)", orderId, reservations.size());
    }

    /** Compensación: devuelve las unidades aunque el producto se haya borrado después de la compra. */
    @Override
    @Transactional(timeout = 5)
    public void release(UUID eventId, Long orderId, List<StockLine> lines) {
        if (!processedEvents.registerIfAbsent(eventId, EventTypes.ORDER_CANCELLED)) {
            log.info("Evento {} (order.cancelled) ya procesado; se ignora el duplicado", eventId);
            return;
        }
        for (StockLine line : sortedByProduct(lines)) {
            productRepository.findByIdIncludingInactive(line.productId()).ifPresentOrElse(product -> {
                product.releaseStock(line.quantity());
                productRepository.save(product);
            }, () -> log.warn("Pedido {}: el producto {} ya no existe y no se le pueden devolver {} unidades",
                    orderId, line.productId(), line.quantity()));
        }
        eventPublisher.publish(new StockReleasedEvent(orderId, Instant.now(clock)));
        log.info("Stock devuelto del pedido cancelado {}", orderId);
    }

    private void fail(Long orderId, ProductErrorCode reason, Long productId, Instant now) {
        eventPublisher.publish(new StockReservationFailedEvent(orderId, reason.code(), productId, now));
        log.info("No se reserva el stock del pedido {}: {} en el producto {}", orderId, reason, productId);
    }

    private static List<StockLine> sortedByProduct(List<StockLine> lines) {
        return lines.stream().sorted(Comparator.comparing(StockLine::productId)).toList();
    }

    private record Reservation(Product product, int quantity) {
    }
}
