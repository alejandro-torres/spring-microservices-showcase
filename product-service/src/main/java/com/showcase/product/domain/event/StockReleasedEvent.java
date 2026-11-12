package com.showcase.product.domain.event;

import java.time.Instant;

/** What a cancelled order had reserved has been returned to the stock. */
public record StockReleasedEvent(Long orderId, Instant occurredAt) {
}
