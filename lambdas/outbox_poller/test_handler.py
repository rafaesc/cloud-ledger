import json
import uuid
from unittest.mock import MagicMock, patch

import boto3
import pytest
from moto import mock_aws
from mypy_boto3_sqs import SQSClient

from outbox_poller.handler import handler


@pytest.fixture(autouse=True)
def env_vars(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("DB_HOST", "localhost")
    monkeypatch.setenv("DB_NAME", "cloudledger")
    monkeypatch.setenv("DB_USER", "postgres")
    monkeypatch.setenv("DB_PASSWORD", "postgres")
    monkeypatch.setenv("AWS_DEFAULT_REGION", "us-east-1")
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "test")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "test")


@mock_aws
@patch("outbox_poller.handler.get_connection")
def test_publishes_unpublished_events_to_sqs(mock_get_conn: MagicMock, monkeypatch: pytest.MonkeyPatch):
    sqs: SQSClient = boto3.client("sqs", region_name="us-east-1") # pyright: ignore[reportUnknownMemberType]
    queue_url = sqs.create_queue(QueueName="outbox.fifo", Attributes={"FifoQueue": "True"})["QueueUrl"]
    monkeypatch.setenv("SQS_QUEUE_URL", queue_url)

    event_id = uuid.uuid4()
    payload = {"type": "MoneyDeposited", "aggregateId": str(event_id)}

    mock_cur = MagicMock()
    mock_cur.fetchall.return_value = [(event_id, payload)]

    mock_conn = MagicMock()
    mock_conn.__enter__.return_value = mock_conn
    mock_conn.cursor.return_value.__enter__.return_value = mock_cur

    mock_get_conn.return_value = mock_conn

    result = handler({}, None)

    assert result == {"published": 1}
    messages = sqs.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=10)["Messages"]
    assert len(messages) == 1

    body = messages[0].get("Body")
    assert body is not None
    assert json.loads(body) == payload


@mock_aws
@patch("outbox_poller.handler.get_connection")
def test_returns_zero_when_no_unpublished_events (mock_get_conn: MagicMock, monkeypatch: pytest.MonkeyPatch):
    sqs: SQSClient = boto3.client("sqs", region_name="us-east-1") # pyright: ignore[reportUnknownMemberType]
    queue_url = sqs.create_queue(QueueName="outbox.fifo", Attributes={"FifoQueue": "True"})["QueueUrl"]
    monkeypatch.setenv("SQS_QUEUE_URL", queue_url)

    mock_cur = MagicMock()
    mock_cur.fetchall.return_value = []

    mock_conn = MagicMock()
    mock_conn.__enter__.return_value = mock_conn
    mock_conn.cursor.return_value.__enter__.return_value = mock_cur

    mock_get_conn.return_value = mock_conn

    result = handler({}, None)

    assert result == {"published": 0}