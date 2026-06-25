from __future__ import annotations

import json
import uuid
from collections.abc import Generator
from datetime import datetime, timezone

import boto3
import psycopg
import pytest
from moto import mock_aws
from testcontainers.postgres import PostgresContainer  # type: ignore[import-untyped]

from outbox_poller.handler import handler

_SCHEMA_DDL = """
CREATE SCHEMA IF NOT EXISTS cloudledger;
CREATE TABLE IF NOT EXISTS cloudledger.outbox (
    event_id        UUID        NOT NULL,
    payload         JSONB       NOT NULL,
    published_at    TIMESTAMP,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sequence_number BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox PRIMARY KEY (event_id)
);
"""


def _connect(pg: PostgresContainer) -> psycopg.Connection:
    return psycopg.connect(
        host=pg.get_container_host_ip(),
        port=int(pg.get_exposed_port(pg.port)),
        dbname=pg.dbname,
        user=pg.username,
        password=pg.password,
    )


@pytest.fixture(scope="session")
def pg_container():
    with PostgresContainer("postgres:16-alpine") as pg:
        conn = _connect(pg)
        conn.autocommit = True
        conn.execute(_SCHEMA_DDL)
        conn.close()
        yield pg


@pytest.fixture(autouse=True)
def env_vars(pg_container: PostgresContainer, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("DB_HOST", pg_container.get_container_host_ip())
    monkeypatch.setenv("DB_PORT", str(pg_container.get_exposed_port(pg_container.port)))
    monkeypatch.setenv("DB_NAME", pg_container.dbname)
    monkeypatch.setenv("DB_USER", pg_container.username)
    monkeypatch.setenv("DB_PASSWORD", pg_container.password)
    monkeypatch.setenv("DB_SSLMODE", "disable")
    monkeypatch.setenv("AWS_DEFAULT_REGION", "us-east-1")
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "test")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "test")


@pytest.fixture()
def db_conn(pg_container: PostgresContainer) -> Generator[psycopg.Connection, None, None]:
    conn = _connect(pg_container)
    yield conn
    conn.execute("TRUNCATE cloudledger.outbox")
    conn.commit()
    conn.close()


@pytest.fixture()
def sqs_queue(monkeypatch: pytest.MonkeyPatch):
    with mock_aws():
        sqs = boto3.client("sqs", region_name="us-east-1")
        url = sqs.create_queue(QueueName="cloudledger-events")["QueueUrl"]
        sqs.purge_queue(QueueUrl=url)
        monkeypatch.setenv("SQS_QUEUE_URL", url)
        yield sqs, url


def _insert_outbox(
    conn: psycopg.Connection,
    payload: dict,
    *,
    published_at: datetime | None = None,
    sequence_number: int = 1,
) -> uuid.UUID:
    event_id = uuid.uuid4()
    conn.execute(
        """
        INSERT INTO cloudledger.outbox (event_id, payload, published_at, sequence_number)
        VALUES (%s, %s::jsonb, %s, %s)
        """,
        (event_id, json.dumps(payload), published_at, sequence_number),
    )
    conn.commit()
    return event_id


class TestOutboxPollerIT:
    def test_publishes_unpublished_rows_to_sqs(
        self, db_conn: psycopg.Connection, sqs_queue
    ) -> None:
        sqs, queue_url = sqs_queue
        _insert_outbox(db_conn, {"type": "MoneyDeposited"}, sequence_number=1)
        _insert_outbox(db_conn, {"type": "MoneyWithdrawn"}, sequence_number=2)

        result = handler({}, None)

        assert result == {"published": 2}
        messages = sqs.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=10)["Messages"]
        assert len(messages) == 2
        published_types = {json.loads(m["Body"])["type"] for m in messages}
        assert published_types == {"MoneyDeposited", "MoneyWithdrawn"}

    def test_marks_published_at_in_db_after_publish(
        self, db_conn: psycopg.Connection, sqs_queue
    ) -> None:
        event_id = _insert_outbox(db_conn, {"type": "AccountOpened"})

        handler({}, None)

        row = db_conn.execute(
            "SELECT published_at FROM cloudledger.outbox WHERE event_id = %s",
            (event_id,),
        ).fetchone()
        assert row is not None and row[0] is not None

    def test_skips_already_published_rows(
        self, db_conn: psycopg.Connection, sqs_queue
    ) -> None:
        sqs, queue_url = sqs_queue
        _insert_outbox(
            db_conn,
            {"type": "AlreadyPublished"},
            published_at=datetime.now(timezone.utc),
            sequence_number=1,
        )
        _insert_outbox(db_conn, {"type": "ShouldPublish"}, sequence_number=2)

        result = handler({}, None)

        assert result == {"published": 1}
        messages = sqs.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=10)["Messages"]
        assert len(messages) == 1
        assert json.loads(messages[0]["Body"])["type"] == "ShouldPublish"

    def test_returns_zero_when_outbox_is_empty(
        self, db_conn: psycopg.Connection, sqs_queue
    ) -> None:
        result = handler({}, None)
        assert result == {"published": 0}
