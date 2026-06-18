CREATE TABLE cloudledger.outbox (
    event_id     UUID      NOT NULL,
    published_at TIMESTAMP,
    CONSTRAINT pk_outbox PRIMARY KEY (event_id),
    CONSTRAINT fk_outbox_event FOREIGN KEY (event_id) REFERENCES cloudledger.events(event_id)
);

CREATE INDEX idx_outbox_unpublished ON cloudledger.outbox (event_id) WHERE published_at IS NULL;
