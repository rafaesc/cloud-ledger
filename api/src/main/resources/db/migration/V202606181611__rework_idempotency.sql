ALTER TABLE cloudledger.events
    DROP CONSTRAINT uq_events_idempotency,
    DROP COLUMN idempotency_key;

CREATE TABLE cloudledger.idempotency_keys (
    idempotency_key TEXT        PRIMARY KEY,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_expiry ON cloudledger.idempotency_keys (expires_at);
