package com.showcase.product.domain.messaging;

import com.showcase.product.domain.event.StockReleasedEvent;
import com.showcase.product.domain.event.StockReservationFailedEvent;
import com.showcase.product.domain.event.StockReservedEvent;

/** Publicación de los eventos de stock (puerto de salida), dentro de la transacción del cambio. */
public interface StockEventPublisher {

    void publish(StockReservedEvent event);

    void publish(StockReservationFailedEvent event);

    void publish(StockReleasedEvent event);
}
