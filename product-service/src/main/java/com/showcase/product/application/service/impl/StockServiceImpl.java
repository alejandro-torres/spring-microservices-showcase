package com.showcase.product.application.service.impl;

import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.events.EventTypes;
import com.showcase.product.application.lock.DistributedLockManager;
import com.showcase.product.application.metrics.StockMetrics;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * product-service's side of the order saga. Each operation registers the processed event, changes the stock and
 * stores its response event in the outbox in ONE transaction: all or nothing.
 * <p>
 * A lack of stock is not a technical error but a business result: no exception is thrown,
 * {@code stock.reservation-failed} is published and the transaction commits (with the processed event registered,
 * so the response is not repeated if the message arrives twice).
 * <p>
 * With several replicas, the order is <b>lock → transaction → commit → unlock</b>: each product's distributed lock
 * is taken before opening the transaction and released after the commit, so another replica never reads
 * stock that has not yet been committed. That is why there is no {@code @Transactional} here (the transaction would
 * open before the lock) but a {@link TransactionTemplate}, which marks exactly where it starts and ends.
 */
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);
    static final String LOCK_PREFIX = "lock:product:";

    private final ProductRepository productRepository;
    private final ProcessedEventRegistry processedEvents;
    private final StockEventPublisher eventPublisher;
    private final DistributedLockManager lockManager;
    private final StockMetrics metrics;
    /** REPEATABLE_READ and a 5 s timeout (defined in the infrastructure, {@code StockTransactionConfig}). */
    @Qualifier("reserveStockTransaction")
    private final TransactionTemplate reserveTransaction;
    @Qualifier("releaseStockTransaction")
    private final TransactionTemplate releaseTransaction;
    private final Clock clock;

    /**
     * All the lines or none: first it checks that all have stock and only then deducts it.
     * <p>
     * Concurrency, from most to least important:
     * <ul>
     *   <li>Distributed per-product lock with Redisson ({@code lock:product:{id}}): two replicas never modify the
     *       same product at once. It avoids conflicts rather than detecting them.</li>
     *   <li>{@code @Version} (optimistic locking): second safety net; if another write still changed the
     *       product (e.g. a manual stock adjustment), the UPDATE fails and the message is retried.</li>
     *   <li>{@code REPEATABLE_READ}: in PostgreSQL, updating a row that another committed transaction has
     *       modified fails with a serialization error, even if the write did not go through the version.</li>
     * </ul>
     */
    @Override
    @Observed(name = "stock.reserve", contextualName = "reserve stock")
    public void reserve(UUID eventId, Long orderId, List<StockLine> lines) {
        lockManager.executeWithLocks(lockKeys(lines), () -> reserveTransaction.execute(status -> {
            doReserve(eventId, orderId, lines);
            return null;
        }));
    }

    /** Compensation: returns the units even if the product was deleted after the purchase. */
    @Override
    @Observed(name = "stock.release", contextualName = "return stock")
    public void release(UUID eventId, Long orderId, List<StockLine> lines) {
        lockManager.executeWithLocks(lockKeys(lines), () -> releaseTransaction.execute(status -> {
            doRelease(eventId, orderId, lines);
            return null;
        }));
    }

    private void doReserve(UUID eventId, Long orderId, List<StockLine> lines) {
        if (!processedEvents.registerIfAbsent(eventId, EventTypes.ORDER_CREATED)) {
            log.info("Event {} (order.created) already processed; ignoring the duplicate", eventId);
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
        metrics.stockReserved();
        log.info("Stock reserved for order {} ({} lines)", orderId, reservations.size());
    }

    private void doRelease(UUID eventId, Long orderId, List<StockLine> lines) {
        if (!processedEvents.registerIfAbsent(eventId, EventTypes.ORDER_CANCELLED)) {
            log.info("Event {} (order.cancelled) already processed; ignoring the duplicate", eventId);
            return;
        }
        for (StockLine line : sortedByProduct(lines)) {
            productRepository.findByIdIncludingInactive(line.productId()).ifPresentOrElse(product -> {
                product.releaseStock(line.quantity());
                productRepository.save(product);
            }, () -> log.warn("Order {}: product {} no longer exists and {} units cannot be returned to it",
                    orderId, line.productId(), line.quantity()));
        }
        eventPublisher.publish(new StockReleasedEvent(orderId, Instant.now(clock)));
        metrics.stockReleased();
        log.info("Stock returned from cancelled order {}", orderId);
    }

    private void fail(Long orderId, ProductErrorCode reason, Long productId, Instant now) {
        eventPublisher.publish(new StockReservationFailedEvent(orderId, reason.code(), productId, now));
        metrics.reservationFailed(reason.code());
        log.info("Not reserving the stock of order {}: {} on product {}", orderId, reason, productId);
    }

    /** One lock per product; the manager sorts them to always take them in the same order. */
    static List<String> lockKeys(List<StockLine> lines) {
        return lines.stream().map(line -> LOCK_PREFIX + line.productId()).distinct().toList();
    }

    private static List<StockLine> sortedByProduct(List<StockLine> lines) {
        return lines.stream().sorted(Comparator.comparing(StockLine::productId)).toList();
    }

    private record Reservation(Product product, int quantity) {
    }
}
