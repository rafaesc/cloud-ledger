ALTER TABLE cloudledger.outbox
    ADD COLUMN payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE cloudledger.outbox
    ALTER COLUMN payload DROP DEFAULT;
