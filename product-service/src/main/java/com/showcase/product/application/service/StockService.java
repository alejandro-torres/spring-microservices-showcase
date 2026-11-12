package com.showcase.product.application.service;

import java.util.List;
import java.util.UUID;

/**
 * Stock reservation and return for the order saga (inbound port of the listeners). {@code eventId}
 * makes each operation idempotent: a repeated message does not touch the stock again.
 */
public interface StockService {

    /** Reserves all the lines or none, and publishes the result (reserved or failed). */
    void reserve(UUID eventId, Long orderId, List<StockLine> lines);

    /** Returns the units of a cancelled order to the stock (compensation). */
    void release(UUID eventId, Long orderId, List<StockLine> lines);
}
