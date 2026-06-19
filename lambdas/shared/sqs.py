from __future__ import annotations

import os
from typing import TYPE_CHECKING

import boto3

if TYPE_CHECKING:
    from mypy_boto3_sqs import SQSClient


def get_sqs_client() -> SQSClient:
    return boto3.client("sqs", endpoint_url=os.environ.get("SQS_ENDPOINT_URL"))