from __future__ import annotations

import json
import logging
import os
from decimal import Decimal
from typing import Any

from shared.dynamo import get_dynamodb_client

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

BALANCE_EVENTS = frozenset({
    "MoneyDeposited", "MoneyWithdrawn",
    "TransferDebited", "TransferCredited", "TransferFailed",
})

DEBIT_EVENTS = frozenset({"MoneyWithdrawn", "TransferDebited", "TransferFailed"})


def handler(event: dict[str, Any], context: object) -> dict[str, Any]:
    records = event["Records"]

    for record in records:
        body = json.loads(record["body"])
        data = body["data"]
        meta = body["meta"]

        event_type = data["type"]
        logger.info("Processing event type=%s", event_type)

        dispatch(event_type, data, meta)

    return {"processed": len(records)}


def dispatch(event_type: str, data: dict[str, Any], meta: dict[str, Any]) -> None:
    if event_type == "AccountOpened":
        handle_account_opened(data)
    elif event_type == "AccountFrozen":
        handle_account_frozen(data)
    elif event_type == "AccountClosed":
        handle_account_closed(data)

    if event_type in BALANCE_EVENTS:
        handle_balance_event(data, meta)
        handle_txn_event(event_type, data)


def handle_account_opened(data: dict[str, Any]) -> None:
    attrs = data["attributes"]
    account_id = attrs["aggregate_id"]
    owner_id = attrs["owner_id"]
    currency = attrs["currency"]
    occurred_on = data["occurred_on"]
    version = data["version"]

    client = get_dynamodb_client()
    table = os.environ["DYNAMODB_TABLE"]

    try:
        client.put_item(
            TableName=table,
            Item={
                "PK":         {"S": f"ACCOUNT#{account_id}"},
                "SK":         {"S": "STATE"},
                "GSI1PK":    {"S": f"OWNER#{owner_id}"},
                "GSI1SK":    {"S": f"ACCOUNT#{account_id}"},
                "owner_id":  {"S": owner_id},
                "status":    {"S": "ACTIVE"},
                "currency":  {"S": currency},
                "opened_at": {"S": occurred_on},
                "version":   {"N": str(version)},
            },
            ConditionExpression="attribute_not_exists(PK)",
        )
        logger.info("STATE item written for account=%s", account_id)
    except client.exceptions.ConditionalCheckFailedException:
        logger.info("Duplicate AccountOpened STATE ignored account=%s", account_id)

    try:
        client.put_item(
            TableName=table,
            Item={
                "PK":            {"S": f"ACCOUNT#{account_id}"},
                "SK":            {"S": "BALANCE"},
                "balance_cents": {"N": "0"},
                "currency":      {"S": currency},
                "version":       {"N": str(version)},
                "updated_at":    {"S": occurred_on},
            },
            ConditionExpression="attribute_not_exists(PK)",
        )
        logger.info("BALANCE item written for account=%s", account_id)
    except client.exceptions.ConditionalCheckFailedException:
        logger.info("Duplicate AccountOpened BALANCE ignored account=%s", account_id)


def handle_account_frozen(data: dict[str, Any]) -> None:
    attrs = data["attributes"]
    account_id = attrs["aggregate_id"]
    version = data["version"]

    client = get_dynamodb_client()
    table = os.environ["DYNAMODB_TABLE"]

    try:
        client.update_item(
            TableName=table,
            Key={
                "PK": {"S": f"ACCOUNT#{account_id}"},
                "SK": {"S": "STATE"},
            },
            UpdateExpression="SET #s = :s, frozen_at = :t, #v = :v",
            ConditionExpression="attribute_not_exists(#v) OR #v < :v",
            ExpressionAttributeNames={"#s": "status", "#v": "version"},
            ExpressionAttributeValues={
                ":s": {"S": "FROZEN"},
                ":t": {"S": data["occurred_on"]},
                ":v": {"N": str(version)},
            },
        )
        logger.info("STATE frozen for account=%s", account_id)
    except client.exceptions.ConditionalCheckFailedException:
        logger.info("Skipping stale AccountFrozen version=%s account=%s", version, account_id)


def handle_account_closed(data: dict[str, Any]) -> None:
    attrs = data["attributes"]
    account_id = attrs["aggregate_id"]
    version = data["version"]

    client = get_dynamodb_client()
    table = os.environ["DYNAMODB_TABLE"]

    try:
        client.update_item(
            TableName=table,
            Key={
                "PK": {"S": f"ACCOUNT#{account_id}"},
                "SK": {"S": "STATE"},
            },
            UpdateExpression="SET #s = :s, closed_at = :t, #v = :v",
            ConditionExpression="attribute_not_exists(#v) OR #v < :v",
            ExpressionAttributeNames={"#s": "status", "#v": "version"},
            ExpressionAttributeValues={
                ":s": {"S": "CLOSED"},
                ":t": {"S": data["occurred_on"]},
                ":v": {"N": str(version)},
            },
        )
        logger.info("STATE closed for account=%s", account_id)
    except client.exceptions.ConditionalCheckFailedException:
        logger.info("Skipping stale AccountClosed version=%s account=%s", version, account_id)


def handle_balance_event(data: dict[str, Any], meta: dict[str, Any]) -> None:
    account_id = data["attributes"]["aggregate_id"]
    version = data["version"]
    balance_cents = int(Decimal(meta["balance_after"]) * 100)

    client = get_dynamodb_client()
    table = os.environ["DYNAMODB_TABLE"]

    try:
        client.update_item(
            TableName=table,
            Key={
                "PK": {"S": f"ACCOUNT#{account_id}"},
                "SK": {"S": "BALANCE"},
            },
            UpdateExpression="SET balance_cents = :b, #v = :v, updated_at = :t",
            ConditionExpression="attribute_not_exists(#v) OR #v < :v",
            ExpressionAttributeNames={"#v": "version"},
            ExpressionAttributeValues={
                ":b": {"N": str(balance_cents)},
                ":v": {"N": str(version)},
                ":t": {"S": data["occurred_on"]},
            },
        )
        logger.info("BALANCE updated account=%s balance_cents=%d", account_id, balance_cents)
    except client.exceptions.ConditionalCheckFailedException:
        logger.info("Skipping stale balance event version=%s account=%s", version, account_id)

    # Advance STATE.version so GET /account always reflects the latest aggregate version
    try:
        client.update_item(
            TableName=table,
            Key={
                "PK": {"S": f"ACCOUNT#{account_id}"},
                "SK": {"S": "STATE"},
            },
            UpdateExpression="SET #v = :v",
            ConditionExpression="attribute_not_exists(#v) OR #v < :v",
            ExpressionAttributeNames={"#v": "version"},
            ExpressionAttributeValues={":v": {"N": str(version)}},
        )
    except client.exceptions.ConditionalCheckFailedException:
        pass


def handle_txn_event(event_type: str, data: dict[str, Any]) -> None:
    attrs = data["attributes"]
    account_id = attrs["aggregate_id"]
    event_id = data["event_id"]
    sequence_number = data["sequence_number"]
    occurred_on = data["occurred_on"]
    amount_cents = int(Decimal(attrs["amount"]) * 100)
    direction = "DEBIT" if event_type in DEBIT_EVENTS else "CREDIT"

    sk = f"TXNS#{str(sequence_number).zfill(20)}"

    item: dict[str, Any] = {
        "PK":              {"S": f"ACCOUNT#{account_id}"},
        "SK":              {"S": sk},
        "event_type":      {"S": event_type},
        "amount_cents":    {"N": str(amount_cents)},
        "direction":       {"S": direction},
        "event_id":        {"S": event_id},
        "sequence_number": {"N": str(sequence_number)},
        "event_at":        {"S": occurred_on},
    }

    if "counterpart_account_id" in attrs:
        item["counterpart_account_id"] = {"S": attrs["counterpart_account_id"]}
    if "transfer_id" in attrs:
        item["transfer_id"] = {"S": attrs["transfer_id"]}

    client = get_dynamodb_client()
    table = os.environ["DYNAMODB_TABLE"]

    try:
        client.put_item(
            TableName=table,
            Item=item,
            ConditionExpression="attribute_not_exists(PK)",
        )
        logger.info("TXNS item written account=%s sk=%s", account_id, sk)
    except client.exceptions.ConditionalCheckFailedException:
        logger.info("Duplicate TXNS item ignored account=%s sk=%s", account_id, sk)
