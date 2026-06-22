from __future__ import annotations

import json
import os
import subprocess
import uuid
from pathlib import Path
from typing import Generator

import boto3
import psycopg
import pytest
import requests

_TERRAFORM_LOCAL = Path(__file__).parent.parent / "terraform" / "envs" / "local"


def _tf_state_attr(module: str, resource_type: str, attribute: str) -> str:
    result = subprocess.run(
        ["terraform", "state", "pull"],
        cwd=_TERRAFORM_LOCAL,
        capture_output=True,
        text=True,
        check=True,
    )
    state = json.loads(result.stdout)
    for resource in state["resources"]:
        if resource.get("module") == module and resource["type"] == resource_type:
            return resource["instances"][0]["attributes"][attribute]
    raise ValueError(f"{module}.{resource_type}.{attribute} not found in terraform state")


@pytest.fixture(scope="session")
def api_url() -> str:
    return os.getenv("E2E_API_URL", "http://localhost:8080")


@pytest.fixture(scope="session")
def api_token() -> str:
    token_url = os.getenv("E2E_TOKEN_URL", "http://localhost:4566/cognito-idp/oauth2/token")
    client_id = os.getenv("E2E_CLIENT_ID") or _tf_state_attr(
        "module.auth", "aws_cognito_user_pool_client", "id"
    )
    client_secret = os.getenv("E2E_CLIENT_SECRET") or _tf_state_attr(
        "module.auth", "aws_cognito_user_pool_client", "client_secret"
    )
    resp = requests.post(
        token_url,
        data={
            "grant_type": "client_credentials",
            "client_id": client_id,
            "client_secret": client_secret,
            "scope": "https://api.getcloudledger.com/write https://api.getcloudledger.com/read",
        },
    )
    resp.raise_for_status()
    return resp.json()["access_token"]


@pytest.fixture(scope="session")
def db() -> Generator[psycopg.Connection, None, None]:
    conn = psycopg.connect(
        os.getenv("E2E_DB_URL", "postgresql://admin:secret123@localhost:7001/cloudledger"),
        autocommit=True,
    )
    conn.execute("SET search_path TO cloudledger")
    yield conn
    conn.close()


@pytest.fixture(scope="session")
def dynamo() -> boto3.client:
    return boto3.client(
        "dynamodb",
        endpoint_url=os.getenv("E2E_DYNAMODB_ENDPOINT", "http://localhost:4566"),
        region_name="us-east-1",
        aws_access_key_id="test",
        aws_secret_access_key="test",
    )


@pytest.fixture(scope="session")
def dynamo_table() -> str:
    return os.getenv("E2E_DYNAMODB_TABLE", "cloudledger-projections")


@pytest.fixture(scope="session")
def happy_path_ids() -> dict[str, str]:
    return {
        "account1": str(uuid.uuid4()),
        "account2": str(uuid.uuid4()),
        "transfer": str(uuid.uuid4()),
    }
