package com.showcase.events;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Tipos de evento con el formato {@code <entidad>.<acción>}. Son parte del contrato y se usan también como
 * routing key al publicar en el exchange del servicio.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventTypes {

    public static final String USER_REGISTERED = "user.registered";
    public static final String USER_DISABLED = "user.disabled";
    public static final String ACCOUNT_LOCKED = "account.locked";
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String STOCK_RESERVED = "stock.reserved";
    public static final String STOCK_RESERVATION_FAILED = "stock.reservation-failed";
    public static final String STOCK_RELEASED = "stock.released";

    /** Versión actual de todos los contratos; cada evento tendrá la suya cuando alguno cambie. */
    public static final int CURRENT_VERSION = 1;
}
