-- W3C context (traceparent) of the request that stored the event: the send and the consumer continue that trace.
ALTER TABLE outbox_event ADD COLUMN trace_parent VARCHAR(55);
