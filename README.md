# CloudLedger

**An audit-grade, event-sourced financial ledger API built on AWS — the primitive layer that payment systems are built on top of.**

![Local mirrors cloud](docs/f628d673-32e5-49db-865e-fc08c325aec9.png)

---

## What is CloudLedger?

Traditional banking systems track account state with a single row: `UPDATE accounts SET balance = 500 WHERE id = 42`. This is efficient, but it destroys history. If a bug, a race condition, or a fraudulent transfer changes that row, there is no record of what the balance was five minutes ago. An auditor asking "what was Alice's balance on March 3rd at 2:14 PM?" has no answer.

CloudLedger never updates or deletes. Every deposit, withdrawal, and transfer is written as an **immutable event** to an append-only Aurora PostgreSQL log: `MoneyDeposited`, `MoneyWithdrawn`, `TransferDebited`, `TransferCredited`. The current balance is always computed by replaying those events — exactly like a bank statement is a list of transactions, not a single number in a cell. Every state the account has ever been in is permanently reconstructable from the log.

The second problem CloudLedger addresses is **concurrent writes**. In a traditional system, two transfers hitting the same account at the same millisecond can silently destroy or create money — the second write overwrites the first without knowing it lost a race. CloudLedger uses **optimistic locking**: every write declares the account version it expects. If another write landed first, the second writer receives a `409 Conflict` and retries with the current state. Money is never lost or invented.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│ CloudLedger                                                         │
│                                                                     │
│  ┌─────────┐  JWT (M2M)   ┌──────────────────────────────────────┐ │
│  │ Cognito │◀─────────────│  Spring Boot 4.1 API  (ECS Fargate)  │ │
│  │  (auth) │─ validates ─▶│  command handlers · JWT · idempotency│ │
│  └─────────┘              └──────────────────────────────────────┘ │
│                           │                                         │
│          ┌────────────────┴──────────────┐                         │
│          │ 1. JDBC                        │ 2. write-through        │
│          │   (events+outbox+accounts, tx) │    {balance, 30 min TTL}│
│          ▼                                ▼                         │
│   ┌─────────────────┐          ┌──────────────┐                    │
│   │  Aurora PgSQL   │          │ ElastiCache  │                    │
│   │  events+outbox  │          │    Redis     │                    │
│   └────────┬────────┘          │  (balance)   │                    │
│            │                   └──────────────┘                    │
│            │  3. EventBridge Scheduler                             │
│            ▼                                                        │
│   ┌──────────────────┐                                             │
│   │  Lambda          │                                             │
│   │  outbox-poller   │                                             │
│   └────────┬─────────┘                                             │
│            │ 4. publish                                            │
│            ▼                                                        │
│      ┌──────────┐   ┌──────────────┐                               │
│      │   SQS    │──▶│    Lambda    │                               │
│      └──────────┘   │   Projector  │                               │
│                     └──────┬───────┘                               │
│                            │ 5. write                              │
│                            ▼                                        │
│                   ┌──────────────────┐                             │
│                   │     DynamoDB     │                             │
│                   │ BALANCE · STATE  │                             │
│                   │ · TXNS#          │                             │
│                   └──────────────────┘                             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Write ordering:** within a command, the API (1) commits `events + outbox + accounts` to Aurora atomically, then (2) **write-throughs** the new balance to Redis (30-minute TTL) so the cache is warm immediately. An EventBridge Scheduler then triggers the (3) **outbox-poller Lambda**, which polls `cloudledger.outbox WHERE published_at IS NULL … FOR UPDATE SKIP LOCKED` and (4) publishes each event to SQS. The (5) **projector Lambda** consumes SQS and writes the durable `BALANCE`, `STATE`, and `TXNS#` items to DynamoDB. The write path itself **never reads** balance from the cache — sufficient-funds validation always rehydrates the aggregate from the Aurora event store.

---

## Key Design Decisions

| Decision | Choice | Why |
|---|---|---|
| Event store | Aurora PostgreSQL (ACID) | Optimistic lock + event insert must be one atomic transaction |
| Read model | DynamoDB + Redis | Sub-10ms balance reads; Redis hot path (30 min TTL), DynamoDB for durable projection |
| Event fan-out | Transactional Outbox → SQS | Guarantees publication even if the API crashes after Aurora commit |
| Concurrency control | Optimistic locking (version counter) | No distributed locks; contention surfaces as a 409, not silent data corruption |
| Idempotency | Per-request UUID key stored in Aurora | Safe client retries; same key always returns the same response |
| Authentication | Cognito M2M (Client Credentials / JWT) | Service-to-service API; JWT `sub` is the Cognito `client_id`, stored as `owner_id` |
| Ownership enforcement | Spring `@PreAuthorize` + `AccountSecurityService` | Declarative, single bean, works uniformly for path params and request body |

---

## Quick Start (Local)

**Prerequisites:** Java 21, Docker, Terraform, Python 3.12 + `uv`

```bash
# Start local infrastructure (Floci: SQS, DynamoDB, Lambda, RDS proxy, Cognito)
docker compose up -d

# Full setup: apply Terraform (incl. Cognito), run DB migrations, build and push Lambda images
bash terraform/scripts/local-bootstrap.sh

# Get the Cognito pool ID created by Terraform
cd terraform/envs/local && terraform output -raw user_pool_id

# Export the JWK set URI so the API can validate tokens at startup
export COGNITO_JWK_SET_URI=http://localhost:4566/<pool_id>/.well-known/jwks.json

# Run the API
cd api && ./gradlew bootRun

# Run API tests
cd api && ./gradlew test

# Run Lambda tests
cd lambdas && uv run pytest
```

> **Flyway migrations** are disabled at Spring Boot startup. The bootstrap script runs them via `./gradlew flywayMigrate`. To run migrations manually:
> ```bash
> cd api && ./gradlew flywayMigrate \
>   -Pflyway.url="jdbc:postgresql://localhost:7001/cloudledger" \
>   -Pflyway.user=admin \
>   -Pflyway.password=secret123
> ```

---

## Key Endpoints

> All write endpoints require:
> - `Authorization: Bearer <jwt>` — Cognito M2M token (Client Credentials grant)
> - `Idempotency-Key: <uuid-v4>` — safe retry guarantee
>
> Account mutations (deposit, withdraw, freeze, close) and transfers return `403` if the JWT `sub` does not match the account's owner. Opening an account registers the JWT `sub` as the permanent owner.

| Method | Path | Description |
|---|---|---|
| `POST` | `/v1/accounts` | Open a new account — body: `{accountId, currency}` |
| `POST` | `/v1/accounts/{accountId}/deposits` | Deposit funds — body: `{amount}` |
| `POST` | `/v1/accounts/{accountId}/withdrawals` | Withdraw funds — body: `{amount}` |
| `POST` | `/v1/transfers` | Transfer between accounts — body: `{sourceAccountId, destinationAccountId, amount, transferId}` |
| `POST` | `/v1/accounts/{accountId}/freeze` | Freeze an account |
| `POST` | `/v1/accounts/{accountId}/close` | Close an account |

> Read endpoints (`GET /balance`, `GET /transactions`, etc.) are planned but not yet implemented.

---

## Repository Layout

```
cloud-ledger/
├── api/                        # Spring Boot 4.1 / Java 21 (ECS Fargate)
│   └── src/main/java/com/getcloudledger/api/
│       ├── account/
│       │   ├── adapter/in/web/     # REST controllers, request DTOs
│       │   ├── adapter/out/cache/  # Redis BalanceCache adapter
│       │   ├── application/        # Command + CommandHandler per use case
│       │   └── domain/             # Account aggregate, events, TransferPolicy
│       └── shared/                 # EventBus, CommandBus, EventStore, JPA entities, SecurityConfig
├── lambdas/                    # Python 3.12 (uv-managed)
│   ├── shared/                 # db.py, sqs.py, dynamo.py connection factories
│   ├── outbox_poller/          # polls outbox → SQS relay
│   └── projector/              # SQS consumer → DynamoDB writer
├── terraform/
│   ├── envs/local/             # Floci-backed local environment (entry point)
│   ├── modules/
│   │   ├── networking/         # VPC, subnets, security groups
│   │   ├── messaging/          # SQS queue (cloudledger-events)
│   │   ├── storage/            # Aurora PostgreSQL cluster, ElastiCache, DynamoDB
│   │   ├── compute/            # ECR, IAM roles, Lambda, EventBridge Scheduler
│   │   └── auth/               # Cognito User Pool, Resource Server, M2M app client
│   └── scripts/
│       ├── local-bootstrap.sh  # full setup from scratch
│       ├── local-destroy.sh    # full teardown
│       └── local-import.sh     # recover orphaned resources into Terraform state
└── README.md
```

---

## The Three Demo Scenarios

These demonstrate the system's core correctness guarantees:

**1. Idempotency demo**
Make a deposit with an `Idempotency-Key`. Replay the exact same request with the same key. The API returns the original `201` response — no second debit, no second event written to Aurora. Proves that client retries after timeouts or network drops are safe by design.

**2. Concurrent conflict demo**
Fire two transfers from the same account simultaneously. One lands first and advances the account version. The second receives `409 Conflict`. The client re-reads, retries with the updated version, and the transfer succeeds. Final balance is exactly correct — money is neither lost nor created.

**3. Async CQRS projection demo**
Issue a transfer. The command commits to Aurora and immediately write-throughs the new balance to Redis. Within ~5 seconds (configurable EventBridge tick), the outbox poller publishes to SQS and the projector Lambda writes the durable `BALANCE` and `TXNS#` items to DynamoDB — providing the eventual read model for future balance and transaction history queries.
