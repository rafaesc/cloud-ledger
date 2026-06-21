from __future__ import annotations

import os
from typing import TYPE_CHECKING

import boto3

if TYPE_CHECKING:
    from mypy_boto3_dynamodb import DynamoDBClient

def get_dynamodb_client() -> DynamoDBClient:
    return boto3.client("dynamodb", endpoint_url=os.environ.get("DYNAMODB_ENDPOINT_URL"))