# CloudLedger Lambdas

Python 3.12 Lambda functions for the async CQRS pipeline. Managed with `uv`.

## Functions

### `outbox_poller`

EventBridge Scheduler triggers this every 5 seconds. It polls `cloudledger.outbox WHERE published_at IS NULL ORDER BY sequence_number FOR UPDATE SKIP LOCKED`, publishes each row's payload to the `cloudledger-events` SQS queue, then bulk-marks rows as `published_at = now()`. `SKIP LOCKED` makes concurrent invocations safe — each instance claims a disjoint set of rows.

### `projector`

SQS consumer. Reads events published by the outbox poller and writes to DynamoDB (single-table design, `PK=ACCOUNT#<id>`):

| SK | Triggered by | Written fields |
|---|---|---|
| `STATE` | `AccountOpened`, `AccountFrozen`, `AccountClosed` | `status`, `owner_id`, `currency`, timestamps, `version` |
| `BALANCE` | `AccountOpened` (zeros), deposit/withdrawal/transfer events | `balance_cents`, `currency`, `version`, `updated_at` |
| `TXNS#<seq>` | `MoneyDeposited/Withdrawn`, `TransferDebited/Credited/Failed` | `amount_cents`, `direction`, `counterpart_account_id?`, `transfer_id?` |

All writes are idempotent via conditional expressions.

### `shared/`

Connection factories shared across functions:
- `db.py` — `psycopg` connection to Aurora
- `sqs.py` — boto3 SQS client
- `dynamo.py` — boto3 DynamoDB client

## Commands

```bash
# Run all tests
cd lambdas && uv run pytest

# Run tests for a single function
cd lambdas && uv run pytest outbox_poller/test_handler.py
cd lambdas && uv run pytest projector/test_handler.py

# Lint
cd lambdas && uv run ruff check .

# Type-check
cd lambdas && uv run mypy .

# Build and push outbox-poller image to Floci ECR (run from lambdas/)
docker build \
  -t localhost:5100/000000000000/us-east-1/cloudledger/outbox-poller:latest \
  -f outbox_poller/Dockerfile .
docker push localhost:5100/000000000000/us-east-1/cloudledger/outbox-poller:latest

# Build and push projector image to Floci ECR (run from lambdas/)
docker build \
  -t localhost:5100/000000000000/us-east-1/cloudledger/projector:latest \
  -f projector/Dockerfile .
docker push localhost:5100/000000000000/us-east-1/cloudledger/projector:latest
```

> **Docker build context must be `lambdas/`** (not the function subdirectory) so `COPY shared/` resolves correctly.

## Local Environment Notes

- Lambda containers reach Floci services via container name: `DB_HOST=floci-rds-cloudledger-postgres`, `SQS_ENDPOINT_URL=http://floci:4566` — not `localhost`.
- Floci ECR registry: `localhost:5100/000000000000/us-east-1/<repo>` (path-style URIs, set by `FLOCI_SERVICES_ECR_URI_STYLE=path` in `docker-compose.yml`).
- Use `psycopg[binary]` in Dockerfiles — the Lambda base image needs the native C extension.
