from __future__ import annotations

import json
import uuid

import boto3
import pytest
from moto import mock_aws

from projector.handler import handler

ACCOUNT_ID      = str(uuid.uuid4())
OWNER_ID        = str(uuid.uuid4())
OTHER_ACCOUNT   = str(uuid.uuid4())
TRANSFER_ID     = str(uuid.uuid4())
EVENT_ID        = str(uuid.uuid4())
TABLE           = "cloudledger-projections"


@pytest.fixture(autouse=True)
def env_vars(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("DYNAMODB_TABLE", TABLE)
    monkeypatch.setenv("AWS_DEFAULT_REGION", "us-east-1")
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "test")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "test")


def make_sqs_event(*payloads: dict) -> dict:
    return {"Records": [{"body": json.dumps(p)} for p in payloads]}


def account_opened_payload(version: int = 1, sequence_number: int = 1) -> dict:
    return {
        "data": {
            "type": "AccountOpened",
            "event_id": EVENT_ID,
            "version": version,
            "sequence_number": sequence_number,
            "occurred_on": "2024-01-15T10:30:00",
            "attributes": {
                "aggregate_id": ACCOUNT_ID,
                "user_id": OWNER_ID,
                "currency": "USD",
            },
        },
        "meta": {},
    }


def money_deposited_payload(
    amount: str = "50.00",
    balance_after: str = "50.00",
    version: int = 2,
    sequence_number: int = 2,
) -> dict:
    return {
        "data": {
            "type": "MoneyDeposited",
            "event_id": EVENT_ID,
            "version": version,
            "sequence_number": sequence_number,
            "occurred_on": "2024-01-15T11:00:00",
            "attributes": {"aggregate_id": ACCOUNT_ID, "amount": amount},
        },
        "meta": {"balance_after": balance_after},
    }


def money_withdrawn_payload(
    amount: str = "20.00",
    balance_after: str = "30.00",
    version: int = 3,
    sequence_number: int = 3,
) -> dict:
    return {
        "data": {
            "type": "MoneyWithdrawn",
            "event_id": EVENT_ID,
            "version": version,
            "sequence_number": sequence_number,
            "occurred_on": "2024-01-15T12:00:00",
            "attributes": {"aggregate_id": ACCOUNT_ID, "amount": amount},
        },
        "meta": {"balance_after": balance_after},
    }


def transfer_debited_payload(
    amount: str = "25.00",
    balance_after: str = "25.00",
    version: int = 4,
    sequence_number: int = 4,
) -> dict:
    return {
        "data": {
            "type": "TransferDebited",
            "event_id": EVENT_ID,
            "version": version,
            "sequence_number": sequence_number,
            "occurred_on": "2024-01-15T13:00:00",
            "attributes": {
                "aggregate_id": ACCOUNT_ID,
                "amount": amount,
                "counterpart_account_id": OTHER_ACCOUNT,
                "transfer_id": TRANSFER_ID,
            },
        },
        "meta": {"balance_after": balance_after},
    }


def transfer_credited_payload(
    amount: str = "25.00",
    balance_after: str = "75.00",
    version: int = 2,
    sequence_number: int = 5,
) -> dict:
    return {
        "data": {
            "type": "TransferCredited",
            "event_id": EVENT_ID,
            "version": version,
            "sequence_number": sequence_number,
            "occurred_on": "2024-01-15T13:01:00",
            "attributes": {
                "aggregate_id": ACCOUNT_ID,
                "amount": amount,
                "counterpart_account_id": OTHER_ACCOUNT,
                "transfer_id": TRANSFER_ID,
            },
        },
        "meta": {"balance_after": balance_after},
    }


def transfer_failed_payload(
    amount: str = "25.00",
    balance_after: str = "50.00",
    version: int = 5,
    sequence_number: int = 6,
) -> dict:
    return {
        "data": {
            "type": "TransferFailed",
            "event_id": EVENT_ID,
            "version": version,
            "sequence_number": sequence_number,
            "occurred_on": "2024-01-15T14:00:00",
            "attributes": {
                "aggregate_id": ACCOUNT_ID,
                "amount": amount,
                "counterpart_account_id": OTHER_ACCOUNT,
                "transfer_id": TRANSFER_ID,
            },
        },
        "meta": {"balance_after": balance_after},
    }


@pytest.fixture()
def dynamo_table():
    with mock_aws():
        dynamo = boto3.client("dynamodb", region_name="us-east-1")
        dynamo.create_table(
            TableName=TABLE,
            KeySchema=[
                {"AttributeName": "PK", "KeyType": "HASH"},
                {"AttributeName": "SK", "KeyType": "RANGE"},
            ],
            AttributeDefinitions=[
                {"AttributeName": "PK", "AttributeType": "S"},
                {"AttributeName": "SK", "AttributeType": "S"},
            ],
            BillingMode="PAY_PER_REQUEST",
        )
        yield dynamo


def get_item(dynamo, sk: str) -> dict:
    return dynamo.get_item(
        TableName=TABLE,
        Key={"PK": {"S": f"ACCOUNT#{ACCOUNT_ID}"}, "SK": {"S": sk}},
    )["Item"]


# ---------------------------------------------------------------------------
# AccountOpened
# ---------------------------------------------------------------------------

class TestAccountOpened:
    def test_writes_state_item(self, dynamo_table) -> None:
        result = handler(make_sqs_event(account_opened_payload()), None)

        item = get_item(dynamo_table, "STATE")
        assert result == {"processed": 1}
        assert item["status"]["S"] == "ACTIVE"
        assert item["currency"]["S"] == "USD"
        assert item["owner_id"]["S"] == OWNER_ID
        assert item["opened_at"]["S"] == "2024-01-15T10:30:00"
        assert item["GSI1PK"]["S"] == f"OWNER#{OWNER_ID}"
        assert item["GSI1SK"]["S"] == f"ACCOUNT#{ACCOUNT_ID}"
        assert item["version"]["N"] == "1"

    def test_writes_balance_item_with_zero_balance(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)

        item = get_item(dynamo_table, "BALANCE")
        assert item["balance_cents"]["N"] == "0"
        assert item["currency"]["S"] == "USD"
        assert item["version"]["N"] == "1"
        assert item["updated_at"]["S"] == "2024-01-15T10:30:00"

    def test_duplicate_delivery_is_noop(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        # second delivery must not raise
        handler(make_sqs_event(account_opened_payload()), None)

        item = get_item(dynamo_table, "STATE")
        assert item["status"]["S"] == "ACTIVE"
        assert item["version"]["N"] == "1"


# ---------------------------------------------------------------------------
# AccountFrozen
# ---------------------------------------------------------------------------

class TestAccountFrozen:
    def test_updates_state_status(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        frozen_payload = {
            "data": {
                "type": "AccountFrozen",
                "event_id": EVENT_ID,
                "version": 2,
                "sequence_number": 2,
                "occurred_on": "2024-01-16T08:00:00",
                "attributes": {"aggregate_id": ACCOUNT_ID, "user_id": OWNER_ID},
            },
            "meta": {},
        }
        handler(make_sqs_event(frozen_payload), None)

        item = get_item(dynamo_table, "STATE")
        assert item["status"]["S"] == "FROZEN"
        assert item["frozen_at"]["S"] == "2024-01-16T08:00:00"
        assert item["version"]["N"] == "2"

    def test_stale_event_does_not_overwrite_newer_state(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        frozen_v3 = {
            "data": {
                "type": "AccountFrozen",
                "event_id": EVENT_ID,
                "version": 3,
                "sequence_number": 3,
                "occurred_on": "2024-01-17T09:00:00",
                "attributes": {"aggregate_id": ACCOUNT_ID, "user_id": OWNER_ID},
            },
            "meta": {},
        }
        frozen_v2 = {
            "data": {
                "type": "AccountFrozen",
                "event_id": EVENT_ID,
                "version": 2,
                "sequence_number": 2,
                "occurred_on": "2024-01-16T08:00:00",
                "attributes": {"aggregate_id": ACCOUNT_ID, "user_id": OWNER_ID},
            },
            "meta": {},
        }
        handler(make_sqs_event(frozen_v3), None)
        # older version delivered out of order — must not overwrite
        handler(make_sqs_event(frozen_v2), None)

        item = get_item(dynamo_table, "STATE")
        assert item["version"]["N"] == "3"
        assert item["frozen_at"]["S"] == "2024-01-17T09:00:00"


# ---------------------------------------------------------------------------
# AccountClosed
# ---------------------------------------------------------------------------

class TestAccountClosed:
    def test_updates_state_status(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        closed_payload = {
            "data": {
                "type": "AccountClosed",
                "event_id": EVENT_ID,
                "version": 2,
                "sequence_number": 2,
                "occurred_on": "2024-01-17T09:00:00",
                "attributes": {"aggregate_id": ACCOUNT_ID, "user_id": OWNER_ID},
            },
            "meta": {},
        }
        handler(make_sqs_event(closed_payload), None)

        item = get_item(dynamo_table, "STATE")
        assert item["status"]["S"] == "CLOSED"
        assert item["closed_at"]["S"] == "2024-01-17T09:00:00"
        assert item["version"]["N"] == "2"

    def test_stale_event_does_not_overwrite_newer_state(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        closed_v4 = {
            "data": {
                "type": "AccountClosed",
                "event_id": EVENT_ID,
                "version": 4,
                "sequence_number": 4,
                "occurred_on": "2024-01-18T10:00:00",
                "attributes": {"aggregate_id": ACCOUNT_ID, "user_id": OWNER_ID},
            },
            "meta": {},
        }
        closed_v2 = {
            "data": {
                "type": "AccountClosed",
                "event_id": EVENT_ID,
                "version": 2,
                "sequence_number": 2,
                "occurred_on": "2024-01-17T09:00:00",
                "attributes": {"aggregate_id": ACCOUNT_ID, "user_id": OWNER_ID},
            },
            "meta": {},
        }
        handler(make_sqs_event(closed_v4), None)
        handler(make_sqs_event(closed_v2), None)

        item = get_item(dynamo_table, "STATE")
        assert item["version"]["N"] == "4"
        assert item["closed_at"]["S"] == "2024-01-18T10:00:00"


# ---------------------------------------------------------------------------
# BALANCE updates
# ---------------------------------------------------------------------------

class TestBalanceEvents:
    def test_money_deposited_updates_balance_cents(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload(amount="50.00", balance_after="50.00")), None)

        item = get_item(dynamo_table, "BALANCE")
        assert item["balance_cents"]["N"] == "5000"
        assert item["version"]["N"] == "2"
        assert item["updated_at"]["S"] == "2024-01-15T11:00:00"

    def test_money_withdrawn_updates_balance_cents(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload()), None)
        handler(make_sqs_event(money_withdrawn_payload(amount="20.00", balance_after="30.00")), None)

        item = get_item(dynamo_table, "BALANCE")
        assert item["balance_cents"]["N"] == "3000"
        assert item["version"]["N"] == "3"

    def test_duplicate_balance_event_is_noop(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload()), None)
        # exact same event delivered again — must not raise and must not change state
        handler(make_sqs_event(money_deposited_payload()), None)

        item = get_item(dynamo_table, "BALANCE")
        assert item["balance_cents"]["N"] == "5000"
        assert item["version"]["N"] == "2"

    def test_stale_balance_event_is_skipped(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload(balance_after="50.00", version=5, sequence_number=5)), None)
        # older version delivered out of order — balance must not regress
        handler(make_sqs_event(money_deposited_payload(balance_after="10.00", version=2, sequence_number=2)), None)

        item = get_item(dynamo_table, "BALANCE")
        assert item["balance_cents"]["N"] == "5000"
        assert item["version"]["N"] == "5"

    def test_balance_cents_converts_decimal_dollars(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload(amount="1.99", balance_after="1.99")), None)

        item = get_item(dynamo_table, "BALANCE")
        assert item["balance_cents"]["N"] == "199"


# ---------------------------------------------------------------------------
# TXNS# items
# ---------------------------------------------------------------------------

class TestTxnEvents:
    def test_money_deposited_writes_txn_with_credit_direction(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload(sequence_number=2)), None)

        sk = f"TXNS#{str(2).zfill(20)}"
        item = get_item(dynamo_table, sk)
        assert item["event_type"]["S"] == "MoneyDeposited"
        assert item["amount_cents"]["N"] == "5000"
        assert item["direction"]["S"] == "CREDIT"
        assert item["event_id"]["S"] == EVENT_ID
        assert item["sequence_number"]["N"] == "2"
        assert item["event_at"]["S"] == "2024-01-15T11:00:00"
        assert "counterpart_account_id" not in item
        assert "transfer_id" not in item

    def test_money_withdrawn_writes_txn_with_debit_direction(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload()), None)
        handler(make_sqs_event(money_withdrawn_payload(sequence_number=3)), None)

        sk = f"TXNS#{str(3).zfill(20)}"
        item = get_item(dynamo_table, sk)
        assert item["direction"]["S"] == "DEBIT"
        assert item["amount_cents"]["N"] == "2000"

    def test_transfer_debited_writes_txn_with_transfer_fields(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload()), None)
        handler(make_sqs_event(transfer_debited_payload(sequence_number=4)), None)

        sk = f"TXNS#{str(4).zfill(20)}"
        item = get_item(dynamo_table, sk)
        assert item["event_type"]["S"] == "TransferDebited"
        assert item["direction"]["S"] == "DEBIT"
        assert item["amount_cents"]["N"] == "2500"
        assert item["counterpart_account_id"]["S"] == OTHER_ACCOUNT
        assert item["transfer_id"]["S"] == TRANSFER_ID

    def test_transfer_credited_writes_txn_with_credit_direction(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(transfer_credited_payload(sequence_number=5)), None)

        sk = f"TXNS#{str(5).zfill(20)}"
        item = get_item(dynamo_table, sk)
        assert item["event_type"]["S"] == "TransferCredited"
        assert item["direction"]["S"] == "CREDIT"
        assert item["counterpart_account_id"]["S"] == OTHER_ACCOUNT
        assert item["transfer_id"]["S"] == TRANSFER_ID

    def test_transfer_failed_writes_txn_with_debit_direction(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload()), None)
        handler(make_sqs_event(transfer_failed_payload(sequence_number=6)), None)

        sk = f"TXNS#{str(6).zfill(20)}"
        item = get_item(dynamo_table, sk)
        assert item["event_type"]["S"] == "TransferFailed"
        assert item["direction"]["S"] == "DEBIT"
        assert item["counterpart_account_id"]["S"] == OTHER_ACCOUNT
        assert item["transfer_id"]["S"] == TRANSFER_ID

    def test_sk_is_zero_padded_to_20_digits(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload(sequence_number=4217)), None)

        expected_sk = "TXNS#00000000000000004217"
        item = get_item(dynamo_table, expected_sk)
        assert item["sequence_number"]["N"] == "4217"

    def test_duplicate_txn_delivery_is_noop(self, dynamo_table) -> None:
        handler(make_sqs_event(account_opened_payload()), None)
        handler(make_sqs_event(money_deposited_payload(sequence_number=2)), None)
        # same event re-delivered — must not raise
        handler(make_sqs_event(money_deposited_payload(sequence_number=2)), None)

        sk = f"TXNS#{str(2).zfill(20)}"
        item = get_item(dynamo_table, sk)
        assert item["direction"]["S"] == "CREDIT"


# ---------------------------------------------------------------------------
# Batch processing
# ---------------------------------------------------------------------------

class TestBatch:
    def test_multi_record_batch_processes_all_records(self, dynamo_table) -> None:
        account2 = str(uuid.uuid4())
        opened2 = {
            "data": {
                "type": "AccountOpened",
                "event_id": str(uuid.uuid4()),
                "version": 1,
                "sequence_number": 10,
                "occurred_on": "2024-01-15T10:35:00",
                "attributes": {
                    "aggregate_id": account2,
                    "user_id": OWNER_ID,
                    "currency": "USD",
                },
            },
            "meta": {},
        }
        result = handler(make_sqs_event(account_opened_payload(), opened2), None)

        assert result == {"processed": 2}
        dynamo_table.get_item(
            TableName=TABLE,
            Key={"PK": {"S": f"ACCOUNT#{ACCOUNT_ID}"}, "SK": {"S": "STATE"}},
        )["Item"]
        dynamo_table.get_item(
            TableName=TABLE,
            Key={"PK": {"S": f"ACCOUNT#{account2}"}, "SK": {"S": "STATE"}},
        )["Item"]
