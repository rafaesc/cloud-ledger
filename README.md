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
│ CloudLedger                                                         │
│                                                                     │
│  ┌──────────────┐  commands  ┌─────────────────────────────┐       │
│  │  Client /    │ ──────────▶│  Spring Boot 3 API          │       │
│  │  k6 tests    │            │  (ECS Fargate)              │       │
│  └──────────────┘            │  - Command handlers         │       │
│                              │  - Query handlers           │       │
│                              │  - JWT (Cognito)            │       │
│                              │  - Idempotency filter       │       │
│                              └──────┬──────────┬────────────┘       │
│                                     │ JDBC     │ async outbox       │
│                          ┌──────────▼──────┐   │                   │
│                          │  Aurora PgSQL   │   │                   │
│                          │  - events table │   │                   │
│                          │  - outbox table │   │                   │
│                          └─────────────────┘   │                   │
│                                                ▼                   │
│  ┌──────────────┐       ┌──────────────┐  ┌────────────────────┐  │
│  │ ElastiCache  │◀───── │    Lambda    │◀─│        SQS         │  │
│  │    Redis     │       │   Projector  │  └────────────────────┘  │
│  │  (balance    │       │              │                           │
│  │   cache)     │       └──────┬───────┘                          │
│  └──────────────┘              │                                   │
│                                ▼                                   │
│                       ┌───────────────┐                            │
│                       │   DynamoDB    │                            │
│                       │ (projections  │                            │
│                       │  BALANCE,     │                            │
│                       │  STATE, TXNS#)│                            │
│                       └───────────────┘                            │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

| Decision | Choice | Why |
|---|---|---|
| Event store | Aurora PostgreSQL (ACID) | Optimistic lock + event insert must be one atomic transaction |
| Read model | DynamoDB + Redis | Sub-10ms balance reads; Redis hot path, DynamoDB for cache misses |
| Event fan-out | Transactional Outbox → SQS | Guarantees publication even if the API crashes after Aurora commit |
| Concurrency control | Optimistic locking (version counter) | No distributed locks; contention surfaces as a 409, not silent data corruption |
| Idempotency | Per-request UUID key stored in Aurora | Safe client retries; same key always returns the same response |

---

## Quick Start (Local)

**Prerequisites:** Java 21, Docker Desktop, AWS CLI (for cloud deploy)

```bash
# Start local dependencies (PostgreSQL + Redis + LocalStack for SQS/DynamoDB)
docker compose up -d
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
| `GET` | `/v1/accounts/{accountId}/balance` | Get current balance (Redis hot path, DynamoDB fallback) |
| `GET` | `/v1/accounts/{accountId}/transactions` | Paginated transaction history (newest-first, cursor-based) |
| `GET` | `/v1/accounts?owner_id={ownerId}` | List all accounts belonging to an owner |

---

## Repository Layout

```
cloud-ledger/
├── api/                     # Spring Boot 3 (ECS Fargate) — Java/Gradle
│   └── src/
│       ├── main/java/com/cloudledger/
│       │   ├── presentation/    # REST controllers, filters (JWT, idempotency, rate limit)
│       │   ├── application/     # command handlers, query handlers
│       │   ├── domain/          # Account aggregate, events, TransferPolicy
│       │   └── infrastructure/  # Aurora, DynamoDB, Redis, SQS repositories
│       └── test/
│           ├── unit/            # pure domain logic, no I/O
│           └── integration/     # Testcontainers (PostgreSQL, Redis) + LocalStack (SQS, DynamoDB)
├── projector/               # Lambda (SQS → DynamoDB + Redis) — TypeScript
│   ├── src/
│   │   └── handler.ts
│   ├── package.json
│   └── tsconfig.json
├── terraform/               # all AWS infrastructure as code
├── docs/
│   ├── cloud-ledger.md      # full reference architecture + ADRs
│   ├── cloud-ledger-tier1.md # build plan (Tier-1 MVP)
│   ├── api-design.md        # HTTP contract + OpenAPI stub
│   ├── dynamodb-schema.md   # DynamoDB single-table design
│   └── domain-model.md      # domain model (aggregates, events, commands) + SQS event schema
└── README.md
```

> The event contract between `api` and `projector` is the JSON payload published to SQS, defined in `docs/domain-model.md`. Each service owns its own types locally.