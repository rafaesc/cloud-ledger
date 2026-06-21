from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timezone
from typing import TYPE_CHECKING, Any

from shared.db import get_connection
from shared.sqs import get_sqs_client

if TYPE_CHECKING:
    from mypy_boto3_sqs import SQSClient

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

def handler(event: dict[str, Any], context: object) -> dict[str, Any]:
    sqs: SQSClient = get_sqs_client()
    queue_url = os.environ["SQS_QUEUE_URL"]

    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT event_id, payload
                FROM cloudledger.outbox
                WHERE published_at IS NULL
                ORDER BY sequence_number
                FOR UPDATE SKIP LOCKED
                """
            )
            rows = cur.fetchall()

            if not rows:
                logger.info("No unpublished events found")
                return {"published": 0}

            for payload in rows:
                sqs.send_message(
                    QueueUrl=queue_url,
                    MessageBody=json.dumps(payload),
                )

            event_ids = [str(row[0]) for row in rows]
            cur.execute(
                """
                UPDATE cloudledger.outbox
                SET published_at = %s
                WHERE event_id = ANY(%s::uuid[])
                """,
                (datetime.now(timezone.utc), event_ids),
            )
            conn.commit()

    logger.info("Published %d events", len(rows))
    return {"published": len(rows)}