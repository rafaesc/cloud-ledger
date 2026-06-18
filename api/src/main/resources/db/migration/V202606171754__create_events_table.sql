CREATE TABLE cloudledger.events (
    event_id       UUID         NOT NULL,
    account_id     UUID         NOT NULL,
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_name     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    version        INTEGER      NOT NULL,
    occurred_on    TIMESTAMP    NOT NULL,
    idempotency_key UUID,
    CONSTRAINT pk_events PRIMARY KEY (event_id),
    CONSTRAINT uq_events_aggregate_version UNIQUE (aggregate_id, version),
    CONSTRAINT uq_events_idempotency UNIQUE (idempotency_key, account_id, aggregate_id, aggregate_type, event_name)
);

CREATE INDEX idx_events_aggregate_id_version ON cloudledger.events (aggregate_id, version);
