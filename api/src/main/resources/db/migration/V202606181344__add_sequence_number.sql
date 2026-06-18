ALTER TABLE cloudledger.events
    ADD COLUMN sequence_number BIGINT GENERATED ALWAYS AS IDENTITY;

CREATE INDEX idx_events_sequence_number ON cloudledger.events (sequence_number);

ALTER TABLE cloudledger.outbox
    ADD COLUMN sequence_number BIGINT NOT NULL DEFAULT 0;
