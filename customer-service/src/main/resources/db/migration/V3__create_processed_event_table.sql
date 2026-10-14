-- Consumo idempotente: eventos de RabbitMQ ya procesados por este servicio.
CREATE TABLE processed_event (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_processed_event_processed_at ON processed_event (processed_at);
