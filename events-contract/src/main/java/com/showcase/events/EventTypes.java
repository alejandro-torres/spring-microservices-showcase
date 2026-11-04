package com.showcase.events;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Event types in the format {@code <entity>.<action>}. They are part of the contract and are also used as the
 * routing key when publishing to the service's exchange.
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

    /** Current version of all contracts; each event will have its own when any of them changes. */
    public static final int CURRENT_VERSION = 1;
}
