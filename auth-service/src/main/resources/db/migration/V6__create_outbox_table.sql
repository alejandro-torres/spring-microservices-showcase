-- Outbox: events stored in the same transaction as the user change, pending send to RabbitMQ.
CREATE TABLE outbox_event (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    exchange       VARCHAR(100) NOT NULL,
    routing_key    VARCHAR(100) NOT NULL,
    -- Full envelope (EventEnvelopeDTO) as it is sent.
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL,
    sent_at        TIMESTAMPTZ,
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'SENT', 'UNROUTED'))
);

-- Partial index: the publisher only looks for pending rows, which are very few compared with those already sent.
CREATE INDEX idx_outbox_event_pending ON outbox_event (created_at, id) WHERE status = 'PENDING';
