"""
Full E2E happy path: write side (API → Aurora → outbox) + async pipeline (outbox-poller → SQS → projector → DynamoDB).
"""
from __future__ import annotations

import time
import uuid
from decimal import Decimal
from uuid import UUID

import psycopg
import pytest
import requests


def _headers(token: str) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": str(uuid.uuid4()),
        "Content-Type": "application/json",
    }


def _dynamo_item(dynamo, table: str, pk: str, sk: str) -> dict | None:
    resp = dynamo.get_item(
        TableName=table,
        Key={"PK": {"S": pk}, "SK": {"S": sk}},
    )
    return resp.get("Item")


def _poll_dynamo(dynamo, table: str, pk: str, sk: str, timeout: int = 60) -> dict:
    deadline = time.time() + timeout
    while time.time() < deadline:
        item = _dynamo_item(dynamo, table, pk, sk)
        if item:
            return item
        time.sleep(2)
    pytest.fail(f"DynamoDB item PK={pk} SK={sk} not found within {timeout}s")


class TestHappyPath:
    # ── Write side ────────────────────────────────────────────────────────────

    def test_open_account1(self, api_url, api_token, happy_path_ids):
        resp = requests.post(
            f"{api_url}/v1/accounts",
            headers=_headers(api_token),
            json={"accountId": happy_path_ids["account1"], "currency": "USD"},
        )
        assert resp.status_code == 201

    def test_open_account2(self, api_url, api_token, happy_path_ids):
        resp = requests.post(
            f"{api_url}/v1/accounts",
            headers=_headers(api_token),
            json={"accountId": happy_path_ids["account2"], "currency": "USD"},
        )
        assert resp.status_code == 201

    def test_deposit(self, api_url, api_token, happy_path_ids):
        resp = requests.post(
            f"{api_url}/v1/accounts/{happy_path_ids['account1']}/deposits",
            headers=_headers(api_token),
            json={"amount": "500.00"},
        )
        assert resp.status_code == 201

    def test_withdraw(self, api_url, api_token, happy_path_ids):
        resp = requests.post(
            f"{api_url}/v1/accounts/{happy_path_ids['account1']}/withdrawals",
            headers=_headers(api_token),
            json={"amount": "50.00"},
        )
        assert resp.status_code == 201

    def test_transfer(self, api_url, api_token, happy_path_ids):
        resp = requests.post(
            f"{api_url}/v1/transfers",
            headers=_headers(api_token),
            json={
                "sourceAccountId": happy_path_ids["account1"],
                "destinationAccountId": happy_path_ids["account2"],
                "amount": "100.00",
                "transferId": happy_path_ids["transfer"],
            },
        )
        assert resp.status_code == 201

    def test_freeze_account2(self, api_url, api_token, happy_path_ids):
        resp = requests.post(
            f"{api_url}/v1/accounts/{happy_path_ids['account2']}/freeze",
            headers=_headers(api_token),
            json={},
        )
        assert resp.status_code == 200

    def test_close_account2(self, api_url, api_token, happy_path_ids):
        resp = requests.post(
            f"{api_url}/v1/accounts/{happy_path_ids['account2']}/close",
            headers=_headers(api_token),
            json={},
        )
        assert resp.status_code == 200

    # ── Aurora verification ───────────────────────────────────────────────────

    def test_aurora_events(self, db: psycopg.Connection, happy_path_ids):
        with db.cursor() as cur:
            cur.execute(
                "SELECT event_name FROM events WHERE aggregate_id IN (%s, %s) ORDER BY sequence_number",
                (UUID(happy_path_ids["account1"]), UUID(happy_path_ids["account2"])),
            )
            names = [row[0] for row in cur.fetchall()]

        assert names == [
            "AccountOpened",    # account1
            "AccountOpened",    # account2
            "MoneyDeposited",
            "MoneyWithdrawn",
            "TransferDebited",
            "TransferCredited",
            "AccountFrozen",
            "AccountClosed",
        ]

    def test_outbox_all_published(self, db: psycopg.Connection, happy_path_ids):
        deadline = time.time() + 30
        while time.time() < deadline:
            with db.cursor() as cur:
                cur.execute(
                    """
                    SELECT COUNT(*) FROM outbox o
                    JOIN events e USING (event_id)
                    WHERE e.aggregate_id IN (%s, %s)
                    AND o.published_at IS NULL
                    """,
                    (UUID(happy_path_ids["account1"]), UUID(happy_path_ids["account2"])),
                )
                unpublished = cur.fetchone()[0]
            if unpublished == 0:
                return
            time.sleep(2)
        pytest.fail(f"{unpublished} outbox row(s) still unpublished after 30s")

    def test_outbox_balance_after_in_meta(self, db: psycopg.Connection, happy_path_ids):
        with db.cursor() as cur:
            cur.execute(
                """
                SELECT e.event_name, o.payload->'meta'->>'balance_after'
                FROM outbox o JOIN events e USING (event_id)
                WHERE e.aggregate_id IN (%s, %s)
                  AND e.event_name IN ('MoneyDeposited','MoneyWithdrawn','TransferDebited','TransferCredited')
                ORDER BY o.sequence_number
                """,
                (UUID(happy_path_ids["account1"]), UUID(happy_path_ids["account2"])),
            )
            rows = cur.fetchall()

        assert rows == [
            ("MoneyDeposited",   "500.00"),
            ("MoneyWithdrawn",   "450.00"),
            ("TransferDebited",  "350.00"),
            ("TransferCredited", "100.00"),
        ]

    # ── DynamoDB projections (async — polls until projector catches up) ────────

    def test_dynamo_account1_balance(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account1']}"
        item = _poll_dynamo(dynamo, dynamo_table, pk, "BALANCE")
        assert item["balance_cents"]["N"] == "35000"   # $350.00

    def test_dynamo_account1_state(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account1']}"
        item = _poll_dynamo(dynamo, dynamo_table, pk, "STATE")
        assert item["status"]["S"] == "ACTIVE"

    def test_dynamo_account1_txns(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account1']}"
        resp = dynamo.query(
            TableName=dynamo_table,
            KeyConditionExpression="PK = :pk AND begins_with(SK, :prefix)",
            ExpressionAttributeValues={
                ":pk": {"S": pk},
                ":prefix": {"S": "TXNS#"},
            },
        )
        assert len(resp["Items"]) == 3  # deposit, withdraw, transfer debit

    def test_dynamo_account2_balance(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account2']}"
        item = _poll_dynamo(dynamo, dynamo_table, pk, "BALANCE")
        assert item["balance_cents"]["N"] == "10000"   # $100.00

    def test_dynamo_account2_state(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account2']}"
        item = _poll_dynamo(dynamo, dynamo_table, pk, "STATE")
        assert item["status"]["S"] == "CLOSED"
