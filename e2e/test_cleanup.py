"""
E2E for the cleanup Lambda: seed expired/old rows in Aurora, invoke the deployed
cleanup function via Floci, and assert it prunes exactly the qualifying rows.
"""
from __future__ import annotations

import json
import uuid
from typing import Any, Generator

import psycopg
import pytest


def _idempotency_count(db: psycopg.Connection, key: str) -> int:
    with db.cursor() as cur:
        cur.execute("SELECT count(*) FROM idempotency_keys WHERE idempotency_key = %s", (key,))
        return cur.fetchone()[0]


def _outbox_count(db: psycopg.Connection, event_id: uuid.UUID) -> int:
    with db.cursor() as cur:
        cur.execute("SELECT count(*) FROM outbox WHERE event_id = %s", (event_id,))
        return cur.fetchone()[0]


def _insert_event(db: psycopg.Connection, event_id: uuid.UUID, aggregate_id: uuid.UUID) -> None:
    """Minimal synthetic event to satisfy the outbox → events foreign key.
    owner_id is the Cognito client_id (VARCHAR), not the account UUID."""
    with db.cursor() as cur:
        cur.execute(
            """
            INSERT INTO events
                (event_id, owner_id, aggregate_id, aggregate_type, event_name, payload, version, occurred_on)
            VALUES (%s, 'e2e-cleanup', %s, 'Account', 'AccountOpened', '{}'::jsonb, 1, now())
            """,
            (event_id, aggregate_id),
        )


class TestCleanup:
    @pytest.fixture(scope="class")
    @classmethod
    def seeded(
        cls, db: psycopg.Connection, lambda_client: Any
    ) -> Generator[dict[str, Any], None, None]:
        ids = {
            "expired_key": f"e2e-cleanup-expired-{uuid.uuid4()}",
            "fresh_key": f"e2e-cleanup-fresh-{uuid.uuid4()}",
            "old_event": uuid.uuid4(),
            "recent_event": uuid.uuid4(),
        }

        with db.cursor() as cur:
            # Idempotency keys: one already expired, one still valid.
            cur.execute(
                """
                INSERT INTO idempotency_keys
                    (idempotency_key, request_hash, response_status, response_body, expires_at)
                VALUES (%s, 'e2e', 200, '{}'::jsonb, now() - interval '1 hour')
                """,
                (ids["expired_key"],),
            )
            cur.execute(
                """
                INSERT INTO idempotency_keys
                    (idempotency_key, request_hash, response_status, response_body, expires_at)
                VALUES (%s, 'e2e', 200, '{}'::jsonb, now() + interval '24 hours')
                """,
                (ids["fresh_key"],),
            )

        # Outbox rows: one published >24h ago (prunable), one published just now (retained).
        _insert_event(db, ids["old_event"], uuid.uuid4())
        _insert_event(db, ids["recent_event"], uuid.uuid4())
        with db.cursor() as cur:
            cur.execute(
                "INSERT INTO outbox (event_id, payload, published_at) "
                "VALUES (%s, '{}'::jsonb, now() - interval '25 hours')",
                (ids["old_event"],),
            )
            cur.execute(
                "INSERT INTO outbox (event_id, payload, published_at) VALUES (%s, '{}'::jsonb, now())",
                (ids["recent_event"],),
            )

        resp = lambda_client.invoke(FunctionName="cleanup")
        assert "FunctionError" not in resp, resp.get("FunctionError")
        ids["result"] = json.loads(resp["Payload"].read())

        yield ids

        # Best-effort teardown so the synthetic event/outbox rows don't accumulate across runs.
        try:
            with db.cursor() as cur:
                cur.execute("DELETE FROM idempotency_keys WHERE idempotency_key = %s", (ids["fresh_key"],))
                cur.execute(
                    "DELETE FROM outbox WHERE event_id = ANY(%s)",
                    ([ids["old_event"], ids["recent_event"]],),
                )
                cur.execute(
                    "DELETE FROM events WHERE event_id = ANY(%s)",
                    ([ids["old_event"], ids["recent_event"]],),
                )
        except Exception:
            pass

    def test_lambda_reports_deletions(self, seeded: dict[str, Any]) -> None:
        result = seeded["result"]
        # Counts are global (other expired rows may exist), so assert at least our seeded rows.
        assert result["idempotency_keys_deleted"] >= 1
        assert result["outbox_deleted"] >= 1

    def test_expired_idempotency_key_deleted(self, db: psycopg.Connection, seeded: dict[str, Any]) -> None:
        assert _idempotency_count(db, seeded["expired_key"]) == 0

    def test_fresh_idempotency_key_retained(self, db: psycopg.Connection, seeded: dict[str, Any]) -> None:
        assert _idempotency_count(db, seeded["fresh_key"]) == 1

    def test_old_published_outbox_row_deleted(self, db: psycopg.Connection, seeded: dict[str, Any]) -> None:
        assert _outbox_count(db, seeded["old_event"]) == 0

    def test_recent_published_outbox_row_retained(self, db: psycopg.Connection, seeded: dict[str, Any]) -> None:
        assert _outbox_count(db, seeded["recent_event"]) == 1
