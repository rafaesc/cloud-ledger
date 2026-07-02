"""
Full E2E happy path: write side (API → Aurora) + async pipeline (SQS → projector → DynamoDB).
"""
from __future__ import annotations

import time
import uuid
from decimal import Decimal
from typing import Any
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


def _dynamo_item(dynamo: Any, table: str, pk: str, sk: str) -> dict[Any, Any] | None:
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


def _poll_dynamo_attr(
    dynamo: Any, table: Any, pk: str, sk: str, attr: str, expected: str, timeout: int = 60
) -> dict:
    """Poll until item[attr] equals expected (comparing N/S string values)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        item = _dynamo_item(dynamo, table, pk, sk)
        if item:
            val = item.get(attr, {})
            if val.get("N") == expected or val.get("S") == expected:
                return item
        time.sleep(2)
    pytest.fail(f"DynamoDB {pk}/{sk}[{attr}]={expected!r} not seen within {timeout}s")


def _poll_dynamo_txns(dynamo: Any, table: Any, pk: str, expected_count: int, timeout: int = 60) -> list:
    deadline = time.time() + timeout
    while time.time() < deadline:
        resp = dynamo.query(
            TableName=table,
            KeyConditionExpression="PK = :pk AND begins_with(SK, :prefix)",
            ExpressionAttributeValues={":pk": {"S": pk}, ":prefix": {"S": "TXNS#"}},
        )
        if len(resp["Items"]) >= expected_count:
            return resp["Items"]
        time.sleep(2)
    pytest.fail(f"Expected {expected_count} TXNS# items for {pk}, timed out after {timeout}s")


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

    def test_transfer(self, api_url:str, api_token: str, happy_path_ids: dict[str, str]):
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

    # ── DynamoDB projections (async — polls until projector catches up) ────────

    def test_dynamo_account1_balance(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account1']}"
        _poll_dynamo_attr(dynamo, dynamo_table, pk, "BALANCE", "balance_cents", "35000")

    def test_dynamo_account1_state(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account1']}"
        item = _poll_dynamo(dynamo, dynamo_table, pk, "STATE")
        assert item["status"]["S"] == "ACTIVE"

    def test_dynamo_account1_txns(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account1']}"
        items = _poll_dynamo_txns(dynamo, dynamo_table, pk, expected_count=3)
        assert len(items) == 3  # deposit, withdraw, transfer debit

    def test_dynamo_account2_balance(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account2']}"
        _poll_dynamo_attr(dynamo, dynamo_table, pk, "BALANCE", "balance_cents", "10000")

    def test_dynamo_account2_state(self, dynamo, dynamo_table, happy_path_ids):
        pk = f"ACCOUNT#{happy_path_ids['account2']}"
        _poll_dynamo_attr(dynamo, dynamo_table, pk, "STATE", "status", "CLOSED")
