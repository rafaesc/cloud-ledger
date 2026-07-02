-- Tracks async projection-rebuild jobs kicked off via POST /v1/admin/projections/rebuild.
-- The job progress (processed_events) is bumped in its own committed transaction as the
-- background worker publishes events to SQS, so GET .../rebuild/{jobId} reflects live progress.
-- Flyway sets search_path=cloudledger, so table names stay unqualified.
CREATE TABLE rebuild_jobs (
    id               UUID PRIMARY KEY,
    account_id       UUID,                        -- NULL = full rebuild (all aggregates)
    status           TEXT      NOT NULL,          -- RUNNING | DONE | FAILED
    total_events     BIGINT    NOT NULL DEFAULT 0,
    processed_events BIGINT    NOT NULL DEFAULT 0,
    error            TEXT,
    started_at       TIMESTAMP NOT NULL,
    finished_at      TIMESTAMP
);
