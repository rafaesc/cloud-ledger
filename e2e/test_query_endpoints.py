"""
E2E tests for read (query) endpoints.

Covers:
  GET /v1/accounts/{accountId}
  GET /v1/accounts/{accountId}/balance
  GET /v1/accounts/{accountId}/transactions
  GET /v1/accounts?owner_id=

Each test class uses the session-scoped `query_setup` fixture, which creates its own
accounts and waits for the async projector pipeline before any read assertions run.
"""
from __future__ import annotations

import base64
import json
import time
import uuid

import pytest
import requests


# ── Helpers ──────────────────────────────────────────────────────────────────


def _owner_id_from_token(token: str) -> str:
    """Decode JWT sub claim without verifying signature."""
    payload = token.split(".")[1]
    padded = payload + "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(padded))["sub"]


def _auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _write_headers(token: str) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": str(uuid.uuid4()),
        "Content-Type": "application/json",
    }


def _poll_dynamo_item(dynamo, table: str, pk: str, sk: str, timeout: int = 60) -> dict:
    deadline = time.time() + timeout
    while time.time() < deadline:
        resp = dynamo.get_item(TableName=table, Key={"PK": {"S": pk}, "SK": {"S": sk}})
        item = resp.get("Item")
        if item:
            return item
        time.sleep(2)
    pytest.fail(f"DynamoDB item PK={pk} SK={sk} not found within {timeout}s")


def _poll_dynamo_txns_count(dynamo, table: str, pk: str, expected: int, timeout: int = 60) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        resp = dynamo.query(
            TableName=table,
            KeyConditionExpression="PK = :pk AND begins_with(SK, :prefix)",
            ExpressionAttributeValues={":pk": {"S": pk}, ":prefix": {"S": "TXNS#"}},
            Select="COUNT",
        )
        if resp["Count"] >= expected:
            return
        time.sleep(2)
    pytest.fail(f"Expected {expected} TXNS# items for {pk}, timed out after {timeout}s")


# ── Session fixture ───────────────────────────────────────────────────────────


@pytest.fixture(scope="session")
def query_setup(api_url, api_token, dynamo, dynamo_table):
    """
    Sets up two accounts with known state, waits for DynamoDB projections.

    account1 (USD): open + deposit $300 + deposit $200 + withdraw $50
      → balance $450.00 (45 000 cents), 3 TXNS# items

    account2 (EUR): open only
      → balance $0.00, 0 TXNS# items
    """
    account1 = str(uuid.uuid4())
    account2 = str(uuid.uuid4())
    owner_id = _owner_id_from_token(api_token)

    def post(path: str, body: dict) -> None:
        r = requests.post(f"{api_url}{path}", headers=_write_headers(api_token), json=body)
        assert r.status_code in (200, 201), f"{path} → {r.status_code}: {r.text}"

    post("/v1/accounts", {"accountId": account1, "currency": "USD"})
    post("/v1/accounts", {"accountId": account2, "currency": "EUR"})
    post(f"/v1/accounts/{account1}/deposits", {"amount": "300.00"})
    post(f"/v1/accounts/{account1}/deposits", {"amount": "200.00"})
    post(f"/v1/accounts/{account1}/withdrawals", {"amount": "50.00"})

    # Wait for the async pipeline to project all items into DynamoDB
    _poll_dynamo_item(dynamo, dynamo_table, f"ACCOUNT#{account1}", "STATE")
    _poll_dynamo_item(dynamo, dynamo_table, f"ACCOUNT#{account1}", "BALANCE")
    _poll_dynamo_item(dynamo, dynamo_table, f"ACCOUNT#{account2}", "STATE")
    _poll_dynamo_item(dynamo, dynamo_table, f"ACCOUNT#{account2}", "BALANCE")
    _poll_dynamo_txns_count(dynamo, dynamo_table, f"ACCOUNT#{account1}", expected=3)

    return {"account1": account1, "account2": account2, "owner_id": owner_id}


# ── GET /v1/accounts/{accountId} ─────────────────────────────────────────────


class TestGetAccount:
    def test_returns_active_account_state(self, api_url, api_token, query_setup):
        account_id = query_setup["account1"]
        resp = requests.get(f"{api_url}/v1/accounts/{account_id}", headers=_auth(api_token))

        assert resp.status_code == 200
        body = resp.json()
        assert body["account_id"] == account_id
        assert body["owner_id"] == query_setup["owner_id"]
        assert body["status"] == "ACTIVE"
        assert body["currency"] == "USD"
        assert body["version"] > 0
        assert body["opened_at"] is not None
        assert body["frozen_at"] is None
        assert body["closed_at"] is None

    def test_eur_account_has_correct_currency(self, api_url, api_token, query_setup):
        account_id = query_setup["account2"]
        resp = requests.get(f"{api_url}/v1/accounts/{account_id}", headers=_auth(api_token))

        assert resp.status_code == 200
        assert resp.json()["currency"] == "EUR"

    def test_nonexistent_account_returns_403(self, api_url, api_token):
        # isOwner queries Aurora — account not found → returns false → Spring Security 403
        resp = requests.get(f"{api_url}/v1/accounts/{uuid.uuid4()}", headers=_auth(api_token))
        assert resp.status_code == 403

    def test_unauthenticated_returns_401(self, api_url, query_setup):
        resp = requests.get(f"{api_url}/v1/accounts/{query_setup['account1']}")
        assert resp.status_code == 401


# ── GET /v1/accounts/{accountId}/balance ──────────────────────────────────────


class TestGetBalance:
    def test_returns_correct_balance_after_deposit_and_withdraw(self, api_url, api_token, query_setup):
        account_id = query_setup["account1"]
        resp = requests.get(f"{api_url}/v1/accounts/{account_id}/balance", headers=_auth(api_token))

        assert resp.status_code == 200
        body = resp.json()
        assert body["account_id"] == account_id
        assert body["balance_cents"] == 45_000  # (300 + 200 - 50) × 100
        assert body["currency"] == "USD"
        assert body["version"] > 0
        assert body["as_of"] is not None

    def test_zero_balance_for_account_with_no_transactions(self, api_url, api_token, query_setup):
        account_id = query_setup["account2"]
        resp = requests.get(f"{api_url}/v1/accounts/{account_id}/balance", headers=_auth(api_token))

        assert resp.status_code == 200
        assert resp.json()["balance_cents"] == 0

    def test_nonexistent_account_returns_403(self, api_url, api_token):
        resp = requests.get(
            f"{api_url}/v1/accounts/{uuid.uuid4()}/balance",
            headers=_auth(api_token),
        )
        assert resp.status_code == 403

    def test_unauthenticated_returns_401(self, api_url, query_setup):
        resp = requests.get(f"{api_url}/v1/accounts/{query_setup['account1']}/balance")
        assert resp.status_code == 401


# ── GET /v1/accounts/{accountId}/transactions ────────────────────────────────


class TestGetTransactions:
    def test_returns_all_three_transactions(self, api_url, api_token, query_setup):
        account_id = query_setup["account1"]
        resp = requests.get(
            f"{api_url}/v1/accounts/{account_id}/transactions",
            headers=_auth(api_token),
        )

        assert resp.status_code == 200
        body = resp.json()
        assert body["account_id"] == account_id
        assert len(body["items"]) == 3
        assert body["next_cursor"] is None

    def test_default_order_is_newest_first(self, api_url, api_token, query_setup):
        account_id = query_setup["account1"]
        resp = requests.get(
            f"{api_url}/v1/accounts/{account_id}/transactions",
            headers=_auth(api_token),
        )

        items = resp.json()["items"]
        # Withdrawal is the most recent event → first in desc order
        assert items[0]["event_type"] == "MoneyWithdrawn"
        assert items[0]["direction"] == "DEBIT"
        assert items[0]["amount_cents"] == 5_000  # $50.00

        # sequence_numbers must be strictly descending
        seqs = [item["sequence_number"] for item in items]
        assert seqs == sorted(seqs, reverse=True)

    def test_asc_order_puts_oldest_first(self, api_url, api_token, query_setup):
        account_id = query_setup["account1"]
        resp = requests.get(
            f"{api_url}/v1/accounts/{account_id}/transactions",
            headers=_auth(api_token),
            params={"order": "asc"},
        )

        items = resp.json()["items"]
        seqs = [item["sequence_number"] for item in items]
        assert seqs == sorted(seqs)
        assert items[0]["event_type"] == "MoneyDeposited"  # oldest event first

    def test_pagination_cursor_traverses_all_items(self, api_url, api_token, query_setup):
        account_id = query_setup["account1"]

        # Page 1: limit=2 → 2 items + cursor
        resp1 = requests.get(
            f"{api_url}/v1/accounts/{account_id}/transactions",
            headers=_auth(api_token),
            params={"limit": 2},
        )
        assert resp1.status_code == 200
        body1 = resp1.json()
        assert len(body1["items"]) == 2
        assert body1["next_cursor"] is not None

        # Page 2: follow cursor → 1 remaining item, no further cursor
        resp2 = requests.get(
            f"{api_url}/v1/accounts/{account_id}/transactions",
            headers=_auth(api_token),
            params={"limit": 2, "cursor": body1["next_cursor"]},
        )
        assert resp2.status_code == 200
        body2 = resp2.json()
        assert len(body2["items"]) == 1
        assert body2["next_cursor"] is None

        # All three sequence numbers are distinct across both pages
        all_seqs = [i["sequence_number"] for i in body1["items"] + body2["items"]]
        assert len(set(all_seqs)) == 3

    def test_empty_transactions_for_account_with_no_balance_events(
        self, api_url, api_token, query_setup
    ):
        account_id = query_setup["account2"]  # only AccountOpened — no TXNS# items
        resp = requests.get(
            f"{api_url}/v1/accounts/{account_id}/transactions",
            headers=_auth(api_token),
        )

        assert resp.status_code == 200
        body = resp.json()
        assert body["items"] == []
        assert body["next_cursor"] is None

    def test_nonexistent_account_returns_403(self, api_url, api_token):
        resp = requests.get(
            f"{api_url}/v1/accounts/{uuid.uuid4()}/transactions",
            headers=_auth(api_token),
        )
        assert resp.status_code == 403


# ── GET /v1/accounts?owner_id= ───────────────────────────────────────────────


class TestListAccounts:
    def test_returns_accounts_owned_by_token_subject(self, api_url, api_token, query_setup):
        owner_id = query_setup["owner_id"]
        resp = requests.get(
            f"{api_url}/v1/accounts",
            headers=_auth(api_token),
            params={"owner_id": owner_id},
        )

        assert resp.status_code == 200
        body = resp.json()
        assert body["owner_id"] == owner_id
        account_ids = {item["account_id"] for item in body["items"]}
        assert query_setup["account1"] in account_ids
        assert query_setup["account2"] in account_ids

    def test_items_contain_correct_currency_and_status(self, api_url, api_token, query_setup):
        owner_id = query_setup["owner_id"]
        resp = requests.get(
            f"{api_url}/v1/accounts",
            headers=_auth(api_token),
            params={"owner_id": owner_id},
        )

        items = resp.json()["items"]
        acc2 = next(i for i in items if i["account_id"] == query_setup["account2"])
        assert acc2["currency"] == "EUR"
        assert acc2["status"] == "ACTIVE"
        assert acc2["opened_at"] is not None

    def test_wrong_owner_id_returns_403(self, api_url, api_token):
        resp = requests.get(
            f"{api_url}/v1/accounts",
            headers=_auth(api_token),
            params={"owner_id": "not-the-jwt-subject"},
        )
        assert resp.status_code == 403

    def test_unauthenticated_returns_401(self, api_url, query_setup):
        resp = requests.get(
            f"{api_url}/v1/accounts",
            params={"owner_id": query_setup["owner_id"]},
        )
        assert resp.status_code == 401
