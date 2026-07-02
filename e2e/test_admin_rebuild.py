"""
E2E for the admin projection-rebuild pipeline.

Proves the whole async loop end-to-end: write side projects to DynamoDB → the read model is
wiped → POST /v1/admin/projections/rebuild (admin scope only) re-publishes the persisted events to
SQS → the projector reconstructs STATE/BALANCE/TXNS# with the *recomputed* balance_after, and the
job row tracks publish-side progress to DONE.
"""
from __future__ import annotations

import time
import uuid
from typing import Any

import pytest
import requests


def _headers(token: str, *, idempotency: bool = True) -> dict[str, str]:
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    if idempotency:
        headers["Idempotency-Key"] = str(uuid.uuid4())
    return headers


def _dynamo_item(dynamo: Any, table: str, pk: str, sk: str) -> dict[Any, Any] | None:
    return dynamo.get_item(TableName=table, Key={"PK": {"S": pk}, "SK": {"S": sk}}).get("Item")


def _poll_balance(dynamo: Any, table: str, pk: str, expected_cents: str, timeout: int = 60) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        item = _dynamo_item(dynamo, table, pk, "BALANCE")
        if item and item.get("balance_cents", {}).get("N") == expected_cents:
            return
        time.sleep(2)
    pytest.fail(f"BALANCE for {pk} did not reach {expected_cents} cents within {timeout}s")


def _query_all(dynamo: Any, table: str, pk: str) -> list[dict[str, Any]]:
    return dynamo.query(
        TableName=table,
        KeyConditionExpression="PK = :pk",
        ExpressionAttributeValues={":pk": {"S": pk}},
    )["Items"]


@pytest.fixture(scope="module")
def rebuild_state() -> dict[str, str]:
    # account: single aggregate we rebuild; job_id: stashed by the trigger test for later polling.
    return {"account": str(uuid.uuid4()), "job_id": ""}


class TestAdminRebuild:
    # ── Write side + initial projection ───────────────────────────────────────

    def test_open_deposit_withdraw(self, api_url, api_token, rebuild_state):
        account = rebuild_state["account"]
        assert requests.post(
            f"{api_url}/v1/accounts",
            headers=_headers(api_token),
            json={"accountId": account, "currency": "USD"},
        ).status_code == 201
        assert requests.post(
            f"{api_url}/v1/accounts/{account}/deposits",
            headers=_headers(api_token),
            json={"amount": "300.00"},
        ).status_code == 201
        assert requests.post(
            f"{api_url}/v1/accounts/{account}/withdrawals",
            headers=_headers(api_token),
            json={"amount": "100.00"},
        ).status_code == 201

    def test_initial_projection_settles(self, dynamo, dynamo_table, rebuild_state):
        # 300 - 100 = 200.00 → 20000 cents. Wait for the normal pipeline to drain before wiping.
        pk = f"ACCOUNT#{rebuild_state['account']}"
        _poll_balance(dynamo, dynamo_table, pk, "20000")

    # ── Wipe the read model ───────────────────────────────────────────────────

    def test_delete_projection(self, dynamo, dynamo_table, rebuild_state):
        pk = f"ACCOUNT#{rebuild_state['account']}"
        items = _query_all(dynamo, dynamo_table, pk)
        assert items, "expected projected items to delete"
        for item in items:
            dynamo.delete_item(TableName=dynamo_table, Key={"PK": item["PK"], "SK": item["SK"]})
        assert _query_all(dynamo, dynamo_table, pk) == []

    # ── Authorization ─────────────────────────────────────────────────────────

    def test_rebuild_forbidden_without_admin_scope(self, api_url, api_token, rebuild_state):
        # The regular read/write token must not be able to trigger a rebuild.
        resp = requests.post(
            f"{api_url}/v1/admin/projections/rebuild",
            params={"account_id": rebuild_state["account"]},
            headers=_headers(api_token),
        )
        assert resp.status_code == 403

    # ── Trigger + track the job ───────────────────────────────────────────────

    def test_trigger_rebuild_returns_202(self, api_url, admin_token, rebuild_state):
        resp = requests.post(
            f"{api_url}/v1/admin/projections/rebuild",
            params={"account_id": rebuild_state["account"]},
            headers=_headers(admin_token),
        )
        assert resp.status_code == 202
        body = resp.json()
        assert body["status"] == "RUNNING"
        assert body["account_id"] == rebuild_state["account"]
        assert body["total_events"] == 3  # AccountOpened + MoneyDeposited + MoneyWithdrawn
        assert body["job_id"]
        rebuild_state["job_id"] = body["job_id"]

    def test_job_completes(self, api_url, admin_token, rebuild_state):
        job_id = rebuild_state["job_id"]
        assert job_id, "trigger test must run first"
        url = f"{api_url}/v1/admin/projections/rebuild/{job_id}"
        deadline = time.time() + 30
        body: dict[str, Any] = {}
        while time.time() < deadline:
            body = requests.get(url, headers=_headers(admin_token, idempotency=False)).json()
            if body["status"] != "RUNNING":
                break
            time.sleep(1)
        assert body["status"] == "DONE", body
        assert body["processed_events"] == body["total_events"] == 3
        assert body["finished_at"]
        assert body["error"] is None

    # ── Projector reconstructs the wiped read model ───────────────────────────

    def test_balance_reprojected(self, dynamo, dynamo_table, rebuild_state):
        # Balance is back to 20000 → proves balance_after was recomputed correctly through the replay.
        pk = f"ACCOUNT#{rebuild_state['account']}"
        _poll_balance(dynamo, dynamo_table, pk, "20000")

    def test_state_and_txns_reprojected(self, dynamo, dynamo_table, rebuild_state):
        pk = f"ACCOUNT#{rebuild_state['account']}"
        deadline = time.time() + 60
        txns: list[dict[str, Any]] = []
        state: dict[str, Any] | None = None
        while time.time() < deadline:
            state = _dynamo_item(dynamo, dynamo_table, pk, "STATE")
            txns = [i for i in _query_all(dynamo, dynamo_table, pk) if i["SK"]["S"].startswith("TXNS#")]
            if state and len(txns) >= 2:
                break
            time.sleep(2)
        assert state is not None and state["status"]["S"] == "ACTIVE"
        assert len(txns) == 2  # deposit + withdraw

    # ── Not-found status ──────────────────────────────────────────────────────

    def test_status_unknown_job_returns_404(self, api_url, admin_token):
        resp = requests.get(
            f"{api_url}/v1/admin/projections/rebuild/{uuid.uuid4()}",
            headers=_headers(admin_token, idempotency=False),
        )
        assert resp.status_code == 404
        assert resp.json()["error"] == "rebuild_job_not_found"
