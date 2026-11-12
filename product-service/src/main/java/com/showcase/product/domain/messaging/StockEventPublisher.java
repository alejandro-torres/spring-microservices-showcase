package com.showcase.product.domain.messaging;

import com.showcase.product.domain.event.StockReleasedEvent;
import com.showcase.product.domain.event.StockReservationFailedEvent;
import com.showcase.product.domain.event.StockReservedEvent;

/** Publishing of stock events (outbound port), within the transaction of the change. */
public interface StockEventPublisher {

    void publish(StockReservedEvent event);

    void publish(StockReservationFailedEvent event);

    void publish(StockReleasedEvent event);
}
