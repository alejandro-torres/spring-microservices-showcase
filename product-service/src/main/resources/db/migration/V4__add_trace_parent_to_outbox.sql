-- Contexto W3C (traceparent) de la petición que guardó el evento: el envío y el consumidor continúan esa traza.
ALTER TABLE outbox_event ADD COLUMN trace_parent VARCHAR(55);
