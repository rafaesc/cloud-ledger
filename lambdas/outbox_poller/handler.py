from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timezone
from typing import TYPE_CHECKING, Any

from opentelemetry import context as otel_context
from opentelemetry.propagate import extract, inject

from shared.db import get_connection
from shared.sqs import get_sqs_client

if TYPE_CHECKING:
    from mypy_boto3_sqs import SQSClient

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)


def _sqs_message_attributes(traceparent: str | None, tracestate: str | None) -> dict[str, Any]:
    """Rebuild the producer's trace context (captured by the API on the outbox row) and inject it
    into a fresh carrier, formatted as SQS message attributes.

    This is the crux of the fallback path: the API published nothing to SQS here, so the agent
    couldn't auto-propagate. By re-injecting the *stored* context the projector parents its work
    to the ORIGINAL request trace instead of a new trace rooted at this poller.
    """
    if not traceparent:
        return {}

    incoming = {"traceparent": traceparent}
    if tracestate:
        incoming["tracestate"] = tracestate
    parent_ctx = extract(incoming)

    carrier: dict[str, str] = {}
    inject(carrier, context=parent_ctx)
    return {
        key: {"DataType": "String", "StringValue": value}
        for key, value in carrier.items()
    }


def handler(event: dict[str, Any], context: object) -> dict[str, Any]:
    sqs: SQSClient = get_sqs_client()
    queue_url = os.environ["SQS_QUEUE_URL"]

    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT event_id, payload, traceparent, tracestate
                FROM cloudledger.outbox
                WHERE published_at IS NULL
                ORDER BY sequence_number
                LIMIT 100
                FOR UPDATE SKIP LOCKED
                """
            )
            rows = cur.fetchall()

            if not rows:
                logger.info("No unpublished events found")
                return {"published": 0}

            for _, payload, traceparent, tracestate in rows:
                message_attributes = _sqs_message_attributes(traceparent, tracestate)

                # Attach the original context as current so the auto-instrumented boto3 SQS span is
                # also parented to the original trace (and agrees with the attributes above).
                parent_ctx = extract(
                    {k: v["StringValue"] for k, v in message_attributes.items()}
                )
                token = otel_context.attach(parent_ctx)
                try:
                    kwargs: dict[str, Any] = {
                        "QueueUrl": queue_url,
                        "MessageBody": json.dumps(payload),
                    }
                    if message_attributes:
                        kwargs["MessageAttributes"] = message_attributes
                    sqs.send_message(**kwargs)
                finally:
                    otel_context.detach(token)

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
