# CloudLedger

**An audit-grade, event-sourced financial ledger API built on AWS — the primitive layer that payment systems are built on top of.**

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
│   Client ──commands──▶  Spring Boot 4.1 API  (ECS Fargate)          │
│                       │  command handlers · JWT · idempotency       │
│          ┌────────────┴──────────────┐                              │
│          │ 1. JDBC                    │ 2. write-through            │
│          │   (events+outbox, one tx)  │    {balance, 30 min TTL}    │
│          ▼                            ▼                             │
│   ┌─────────────────┐          ┌──────────────┐                     │
│   │  Aurora PgSQL   │          │ ElastiCache  │                     │
│   │  events+outbox  │          │    Redis     │                     │
│   └────────┬────────┘          │  (balance)   │                     │
│            │                   └──────────────┘                     │
│            │  3. EventBridge Scheduler                              │
│            ▼                                                        │
│   ┌──────────────────┐                                              │
│   │  Lambda          │                                              │
│   │  outbox-poller   │                                              │
│   └────────┬─────────┘                                              │
│            │ 4. publish                                             │
│            ▼                                                        │
│      ┌──────────┐   ┌──────────────┐                                │
│      │   SQS    │──▶│    Lambda    │                                │
│      └──────────┘   │   Projector  │                                │
│                     └──────┬───────┘                                │
│                            │ 5. write                               │
│                            ▼                                        │
│                   ┌──────────────────┐                              │
│                   │     DynamoDB     │                              │
│                   │ BALANCE · STATE  │                              │
│                   │ · TXNS#          │                              │
│                   └──────────────────┘                              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

> **Write ordering (ADR-012):** within a command, the API (1) commits `events + outbox` to Aurora atomically, then (2) **write-throughs** the new balance to Redis (30-minute TTL) so the cache is fresh before the async fan-out. An EventBridge Scheduler then triggers the (3) **outbox-poller Lambda**, which polls `cloudledger.outbox WHERE published_at IS NULL … FOR UPDATE SKIP LOCKED` and (4) publishes each event to SQS. The (5) **projector Lambda** consumes SQS and writes the durable `BALANCE`, `STATE`, and `TXNS#` items to DynamoDB. The write path itself **never reads** balance from the cache or projection — sufficient-funds validation always rehydrates the aggregate from the Aurora event store (no double-spend, no bypass of the version fence).

---

## Key Design Decisions

| Decision | Choice | Why |
|---|---|---|
| Event store | Aurora PostgreSQL (ACID) | Optimistic lock + event insert must be one atomic transaction |
| Read model | DynamoDB + Redis | Sub-10ms balance reads; Redis hot path, DynamoDB for cache misses |
| Read-your-writes | Command-path Redis write-through (version-CAS) | `GET /balance` reflects the caller's just-committed transfer with no CQRS lag window; projector backstop covers the Redis-down case (ADR-012) |
| Event fan-out | Transactional Outbox → SQS | Guarantees publication even if the API crashes after Aurora commit |
| Concurrency control | Optimistic locking (version counter) | No distributed locks; contention surfaces as a 409, not silent data corruption |
| Idempotency | Per-request UUID key stored in Aurora | Safe client retries; same key always returns the same response |

---

## Quick Start (Local)

**Prerequisites:** Java 21, Docker Desktop, AWS CLI (for cloud deploy)

```bash
# Start local dependencies (PostgreSQL + Redis + LocalStack for SQS/DynamoDB)
docker compose up -d

# Run database migrations
./mvnw flyway:migrate

# Run the API
./mvnw spring-boot:run

# Run unit + integration tests
./mvnw verify
```

---

## Key Endpoints

> All writes require `Idempotency-Key: <uuid-v4>` header.

| Method | Path | Intent |
|---|---|---|
| `POST` | `/v1/accounts` | Open a new account |
| `POST` | `/v1/accounts/{accountId}/deposits` | Deposit funds into an account |
| `POST` | `/v1/accounts/{accountId}/withdrawals` | Withdraw funds from an account |
| `POST` | `/v1/transfers` | Transfer funds between two accounts |
| `POST` | `/v1/accounts/{accountId}/freeze` | Freeze an account |
| `POST` | `/v1/accounts/{accountId}/close` | Close an account |
| `GET` | `/v1/accounts/{accountId}` | Get account metadata and status |
| `GET` | `/v1/accounts/{accountId}/balance` | Get current balance (Redis hot path w/ command-path write-through for read-your-writes, DynamoDB fallback) |
| `GET` | `/v1/accounts/{accountId}/transactions` | Paginated transaction history (newest-first, cursor-based) |
| `GET` | `/v1/accounts?owner_id={ownerId}` | List all accounts belonging to an owner |

Full request/response schemas, error codes, and the OpenAPI 3.1 stub are in [`docs/api-design.md`](docs/api-design.md).

---

## Repository Layout

```
cloud-ledger/
├── src/main/java/com/cloudledger/
│   ├── presentation/        # REST controllers, filters (JWT, idempotency, rate limit)
│   ├── application/         # command handlers, query handlers
│   ├── domain/              # Account aggregate, events, TransferPolicy
│   └── infrastructure/      # Aurora, DynamoDB, Redis, SQS repositories
├── src/test/
│   ├── unit/                # pure domain logic, no I/O
│   └── integration/         # Testcontainers (PostgreSQL, Redis) + LocalStack (SQS, DynamoDB)
├── terraform/               # all AWS infrastructure as code
├── docs/
│   ├── cloud-ledger.md      # full reference architecture + ADRs
│   ├── cloud-ledger-tier1.md # build plan (Tier-1 MVP)
│   ├── api-design.md        # HTTP contract + OpenAPI stub
│   ├── dynamodb-schema.md   # DynamoDB single-table design
│   └── domain-model.md      # domain model (aggregates, events, commands)
└── README.md
```

---

## The Three Demo Scenarios

These are the strongest live demonstrations of the system's correctness guarantees:

**1. Idempotency demo**
Make a deposit with an `Idempotency-Key`. Replay the exact same request with the same key. The API returns the original `201` response with the original body — no second debit, no second event written to Aurora. Proves that client retries (after timeouts or network drops) are safe by design.

**2. Concurrent conflict demo**
Fire two transfers from the same account simultaneously. One lands first and advances the account version. The second receives `409 Conflict` with the current version in the response body. The client re-reads, retries with the updated version, and the transfer succeeds. Final balance is exactly correct — money is neither lost nor created.

**3. CQRS projection lag + read-your-writes demo**
Issue a transfer. The command returns immediately after the Aurora commit and write-throughs the new balance to Redis under a version-CAS, so a `GET /balance` fired right away is already correct (read-your-writes, ADR-012). Within ~2 seconds the outbox poller publishes to SQS and the Lambda projector updates the durable DynamoDB projection. The `X-Projection-Lag-Ms` response header surfaces the measured lag in real time — ≈0 on a write-through hit, rising only when Redis is down and the read falls back to the projector-populated value.

> The event contract between `api` and `projector` is the JSON payload published to SQS, defined in `docs/domain-model.md`. Each service owns its own types locally.