-- Motivo con el que product-service rechazó la reserva de stock (INSUFFICIENT_STOCK, PRODUCT_NOT_FOUND).
ALTER TABLE orders ADD COLUMN rejection_reason VARCHAR(100);

-- Outbox: eventos guardados en la misma transacción que el pedido, pendientes de enviar a RabbitMQ.
CREATE TABLE outbox_event (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    exchange       VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(100) NOT NULL,
    -- Sobre completo (EventEnvelopeDTO) tal como se envía.
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL,
    sent_at        TIMESTAMPTZ,
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'SENT', 'UNROUTED'))
);

-- Índice parcial: el publicador solo busca los pendientes, que son muy pocos frente a los ya enviados.
CREATE INDEX idx_outbox_event_pending ON outbox_event (created_at, id) WHERE status = 'PENDING';

-- Consumo idempotente: eventos de RabbitMQ ya procesados por este servicio.
CREATE TABLE processed_event (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_processed_event_processed_at ON processed_event (processed_at);
