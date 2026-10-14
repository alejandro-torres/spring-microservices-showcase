package com.showcase.product.application.service.impl;

import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.events.EventTypes;
import com.showcase.product.application.lock.DistributedLockManager;
import com.showcase.product.application.service.StockLine;
import com.showcase.product.application.service.StockService;
import com.showcase.product.domain.event.StockReleasedEvent;
import com.showcase.product.domain.event.StockReservationFailedEvent;
import com.showcase.product.domain.event.StockReservedEvent;
import com.showcase.product.domain.exception.ProductErrorCode;
import com.showcase.product.domain.messaging.StockEventPublisher;
import com.showcase.product.domain.model.Product;
import com.showcase.product.domain.repository.ProductRepository;
import io.micrometer.observation.annotation.Observed;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lado de product-service de la saga de pedidos. Cada operación registra el evento procesado, cambia el stock y
 * guarda su evento de respuesta en el outbox en UNA transacción: o pasa todo o nada.
 * <p>
 * Que falte stock no es un error técnico sino un resultado de negocio: no se lanza ninguna excepción, se publica
 * {@code stock.reservation-failed} y la transacción hace commit (con el evento procesado registrado, para no
 * repetir la respuesta si el mensaje llega dos veces).
 * <p>
 * Con varias réplicas, el orden es <b>lock → transacción → commit → unlock</b>: el lock distribuido de cada
 * producto se coge antes de abrir la transacción y se suelta después del commit, para que otra réplica nunca lea
 * un stock que todavía no se ha confirmado. Por eso aquí no hay {@code @Transactional} (la transacción se abriría
 * antes que el lock) sino un {@link TransactionTemplate}, que delimita exactamente dónde empieza y acaba.
 */
@Service
public class StockServiceImpl implements StockService {

    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);
    static final String LOCK_PREFIX = "lock:product:";

    private final ProductRepository productRepository;
    private final ProcessedEventRegistry processedEvents;
    private final StockEventPublisher eventPublisher;
    private final DistributedLockManager lockManager;
    private final TransactionTemplate reserveTransaction;
    private final TransactionTemplate releaseTransaction;
    private final Clock clock;

    public StockServiceImpl(ProductRepository productRepository, ProcessedEventRegistry processedEvents,
                            StockEventPublisher eventPublisher, DistributedLockManager lockManager,
                            PlatformTransactionManager transactionManager, Clock clock) {
        this.productRepository = productRepository;
        this.processedEvents = processedEvents;
        this.eventPublisher = eventPublisher;
        this.lockManager = lockManager;
        this.reserveTransaction = new TransactionTemplate(transactionManager);
        this.reserveTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.reserveTransaction.setTimeout(5);
        this.releaseTransaction = new TransactionTemplate(transactionManager);
        this.releaseTransaction.setTimeout(5);
        this.clock = clock;
    }

    /**
     * Todas las líneas o ninguna: primero se comprueba que todas tienen stock y solo entonces se descuentan.
     * <p>
     * Concurrencia, de más a menos importante:
     * <ul>
     *   <li>Lock distribuido por producto con Redisson ({@code lock:product:{id}}): dos réplicas nunca modifican el
     *       mismo producto a la vez. Evita los conflictos en lugar de detectarlos.</li>
     *   <li>{@code @Version} (bloqueo optimista): segunda red de seguridad; si aun así otra escritura cambió el
     *       producto (p. ej. un ajuste manual de stock), el UPDATE falla y el mensaje se reintenta.</li>
     *   <li>{@code REPEATABLE_READ}: en PostgreSQL, actualizar una fila que otra transacción confirmada ha
     *       modificado falla con un error de serialización, aunque la escritura no pasara por la versión.</li>
     * </ul>
     */
    @Override
    @Observed(name = "stock.reserve", contextualName = "reservar stock")
    public void reserve(UUID eventId, Long orderId, List<StockLine> lines) {
        lockManager.executeWithLocks(lockKeys(lines), () -> reserveTransaction.execute(status -> {
            doReserve(eventId, orderId, lines);
            return null;
        }));
    }

    /** Compensación: devuelve las unidades aunque el producto se haya borrado después de la compra. */
    @Override
    @Observed(name = "stock.release", contextualName = "devolver stock")
    public void release(UUID eventId, Long orderId, List<StockLine> lines) {
        lockManager.executeWithLocks(lockKeys(lines), () -> releaseTransaction.execute(status -> {
            doRelease(eventId, orderId, lines);
            return null;
        }));
    }

    private void doReserve(UUID eventId, Long orderId, List<StockLine> lines) {
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

    private void doRelease(UUID eventId, Long orderId, List<StockLine> lines) {
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

    /** Un lock por producto; el gestor los ordena para cogerlos siempre en el mismo orden. */
    static List<String> lockKeys(List<StockLine> lines) {
        return lines.stream().map(line -> LOCK_PREFIX + line.productId()).distinct().toList();
    }

    private static List<StockLine> sortedByProduct(List<StockLine> lines) {
        return lines.stream().sorted(Comparator.comparing(StockLine::productId)).toList();
    }

    private record Reservation(Product product, int quantity) {
    }
}
