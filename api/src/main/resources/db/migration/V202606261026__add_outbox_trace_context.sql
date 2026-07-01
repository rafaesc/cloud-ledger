-- Carries the W3C trace context (traceparent + optional tracestate) captured at the moment
-- the outbox row is written, so the outbox-poller can re-inject it into the SQS message and
-- keep the distributed trace unbroken across the SQS-failure fallback path.
-- Nullable: pre-existing rows and any event produced outside an active trace have no context.
-- Flyway sets search_path=cloudledger, so the table name is unqualified.
ALTER TABLE outbox ADD COLUMN traceparent TEXT;
ALTER TABLE outbox ADD COLUMN tracestate  TEXT;
