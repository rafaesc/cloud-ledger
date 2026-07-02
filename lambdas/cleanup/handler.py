from __future__ import annotations

from typing import Any

from shared.db import get_connection


def handler(_: dict[str, Any], context: object) -> dict[str, int]:
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                DELETE FROM cloudledger.idempotency_keys
                WHERE idempotency_key IN (
                    SELECT idempotency_key
                    FROM cloudledger.idempotency_keys
                    WHERE expires_at < now()
                    LIMIT 500
                )
                """
            )
            idempotency_deleted = cur.rowcount

            cur.execute(
                """
                DELETE FROM cloudledger.outbox
                WHERE published_at IS NOT NULL
                    AND published_at < now() - interval '24 hours'
                """
            )
            outbox_deleted = cur.rowcount

            conn.commit()

    return {
        "idempotency_keys_deleted": idempotency_deleted,
        "outbox_deleted": outbox_deleted,
    }