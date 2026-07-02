# CloudLedger — k6 Load & Verification Suite (W6 "Prove it")

This is the **Tier-1 slice** of the "Prove it" deliverable: a CI smoke gate, a modest load run,
a concurrent-conflict correctness demo, and a steady read-balance run. The methodology — *what
each run proves and which thresholds k6 owns vs. the CloudWatch dashboard* — is the point here,
not raw throughput.

> **Tier-2 is deferred** (5,000 TPS / 2-hour soak, hot-account injection at scale, FIS chaos,
> ECS Fargate Spot k6 fleet). Those live as clearly-marked, non-runnable stubs under
> [`scenarios/tier2/`](scenarios/tier2/). Do not run them.

---

## Layout

```
k6/
├── config/
│   ├── environments.js    # base URL + Cognito params, all from __ENV
│   └── thresholds.js      # k6-observable thresholds only
├── lib/
│   ├── auth.js            # Cognito token fetch + per-runtime cache
│   ├── client.js          # HTTP wrapper: JWT + fresh Idempotency-Key + tags
│   ├── ledger.js          # domain helpers: open/deposit/withdraw/transfer/getBalance + pollBalance
│   ├── checks.js          # projection_lag Trend (read-after-write) + conflict counters
│   └── uuid.js            # dependency-free UUIDv4 (no remote jslib import → CI-safe)
├── data/
│   └── seed.js            # SharedArray of seed owners
├── scenarios/
│   ├── smoke.js           # CI GATE — 1 VU × 10 transfers E2E
│   ├── load-transfers.js  # ramping-arrival-rate → ~300 req/s
│   ├── conflict.js        # ⭐ correctness: one 201 + N-1 conflicts, then loser retry
│   ├── read-balance.js    # constant-arrival-rate GET /balance
│   └── tier2/             # DEFERRED stubs (do not run)
└── results/               # gitignored; JSON summaries + screenshots (keeps .gitkeep)
```

**Design rule:** scenarios are *declarative*. All auth, idempotency-key generation, `expected_version`
plumbing, tagging, and projection-lag measurement live in `lib/` — never in a scenario file.

---

## Prerequisites

1. **Install k6** — <https://grafana.com/docs/k6/latest/set-up/install-k6/> (e.g. `brew install k6`).
2. **A running target.** Local: `bash terraform/scripts/local-bootstrap.sh` (Floci + Lambdas +
   schema) **and** the Spring API running. Or a dev AWS deployment.
3. **Cognito M2M credentials** for the client-credentials grant. Pull them from Terraform state:

   ```bash
   # Run these from terraform/envs/local (NOT terraform/scripts — no state lives there).
   # -chdir works from anywhere:
   export CLIENT_ID=$(terraform -chdir=terraform/envs/local output -raw cognito_client_id)
   export CLIENT_SECRET=$(terraform -chdir=terraform/envs/local output -json cognito_client_secret | jq -r .)
   # NOTE: do NOT use `terraform output alb_dns_name` for BASE_URL locally — it returns a
   # <id>.elb.localhost name that does NOT resolve on WSL2. Floci binds the ALB listener on
   # host port 80, so the reachable local URL is simply:
   export BASE_URL=http://localhost
   # Local Cognito token endpoint (Floci):
   export TOKEN_URL="http://localhost:4566/cognito-idp/oauth2/token"
   ```

   For **prod/dev AWS**, `TOKEN_URL` is the hosted-UI domain
   (`https://cloudledger-prod.auth.us-east-1.amazoncognito.com/oauth2/token`) and `BASE_URL` is
   the ALB DNS name.

### Environment variables

| Var | Required | Default | Notes |
|---|---|---|---|
| `BASE_URL` | yes (non-local) | `http://localhost` | API base URL (ALB) |
| `TOKEN_URL` | yes (non-local) | Floci Cognito endpoint | OAuth2 token endpoint |
| `CLIENT_ID` / `CLIENT_SECRET` | **yes** | — | Cognito M2M app client |
| `SCOPE` | no | `.../write .../read` | OAuth scopes |
| `CURRENCY` | no | `USD` | account currency |
| Per-scenario tuning | no | see file | `TARGET_RPS`, `POOL_SIZE`, `READ_RPS`, `DURATION`, `CONFLICT_N`, `CONFLICT_STATUS`, … |

---

## Run commands

```bash
# Smoke (CI gate) — exits non-zero on any failed check or projection_lag p99 ≥ 5s
BASE_URL=$BASE_URL k6 run k6/scenarios/smoke.js

# Modest load — ~300 req/s transfers for ~3.5 min (ramp 1m, hold 2m, drain 30s)
BASE_URL=$BASE_URL k6 run k6/scenarios/load-transfers.js
#   tune: -e TARGET_RPS=300 -e POOL_SIZE=100 -e HOLD=2m

# Conflict correctness demo (⭐)
BASE_URL=$BASE_URL k6 run k6/scenarios/conflict.js
#   tune: -e CONFLICT_N=2  -e CONFLICT_STATUS=409   (see "Contract prerequisites")

# Steady read load — GET /balance at 200 req/s for 2 min
BASE_URL=$BASE_URL k6 run k6/scenarios/read-balance.js

# Capture a machine-readable summary into results/ (gitignored)
BASE_URL=$BASE_URL k6 run --summary-export k6/results/smoke-$(date +%s).json k6/scenarios/smoke.js
```

`CLIENT_ID`, `CLIENT_SECRET`, `TOKEN_URL` are read from the shell env (exported above), so they
don't need repeating on each command. Use `-e KEY=value` to override any `__ENV` var inline.

### Running against local Floci

The suite **defaults** to local Floci (`BASE_URL=http://localhost` = the Floci ALB on :80;
`TOKEN_URL=http://localhost:4566/...`), so it runs there out of the box — with two adjustments,
because Floci is a single local box, not dev AWS:

```bash
# 1. Bring the stack up + deploy Lambdas + migrate, and start the API (see repo CLAUDE.md)
bash terraform/scripts/local-bootstrap.sh
# 2. Pull M2M creds from the LOCAL terraform env (-chdir works from the repo root)
export CLIENT_ID=$(terraform -chdir=terraform/envs/local output -raw cognito_client_id)
export CLIENT_SECRET=$(terraform -chdir=terraform/envs/local output -json cognito_client_secret | jq -r .)
export BASE_URL=http://localhost          # Floci ALB on host :80 — NOT the .elb.localhost output
export TOKEN_URL="http://localhost:4566/cognito-idp/oauth2/token"
# sanity check: should print 200
curl -s -o /dev/null -w '%{http_code}\n' "$BASE_URL/actuator/health"

# 3. Smoke + conflict, with a projection bound that fits Floci (its SQS→Lambda→DynamoDB
#    projection can take tens of seconds; the e2e suite allows 60s). PROJECTION_LAG_MS
#    stretches both the poll timeout and the projection_lag threshold.
BASE_URL=$BASE_URL PROJECTION_LAG_MS=60000 k6 run k6/scenarios/smoke.js
BASE_URL=$BASE_URL PROJECTION_LAG_MS=60000 k6 run k6/scenarios/conflict.js
```

> If the API is a host `bootRun` instead of the Floci ECS task, set `BASE_URL=http://localhost:8080`.

**What runs meaningfully on Floci vs. what doesn't:**

| Scenario | Local Floci | Why |
|---|---|---|
| `smoke.js` | ✅ with `PROJECTION_LAG_MS=60000` | Correctness/E2E — the 5s freshness SLO is a dev-AWS number; Floci projection is slower |
| `conflict.js` | ✅ with `PROJECTION_LAG_MS=60000` | Pure correctness (optimistic-lock race + no double-spend); throughput-independent |
| `load-transfers.js` | ⚠️ script-check only, at low rate (`-e TARGET_RPS=20`) | 300 req/s and the `p99 < 200ms` SLO are **dev-AWS sizing**; a single Floci box will saturate and the latency/error thresholds will (correctly) fail |
| `read-balance.js` | ⚠️ low rate (`-e READ_RPS=20`) | Same — the read SLOs assume dev-AWS ElastiCache/DynamoDB, not Floci |

Use Floci to prove **correctness** (smoke + conflict); run the **load/latency** scenarios against
dev AWS sizing, where the thresholds are meaningful.

---

## What each scenario proves → W6 DoD

| Scenario | Proves | W6 DoD item |
|---|---|---|
| `smoke.js` | E2E write→project→read on 10 transfers; idempotent replay doesn't double-apply; projection lag < 5s | **CI gate** — write path + projection freshness. Wire into GitHub Actions (see below). |
| `load-transfers.js` | Aurora write path holds p99 < 200ms and < 1% errors at ~300 req/s | Modest sustained-load run |
| `conflict.js` | Optimistic lock: exactly one 201 + N-1 conflicts on same-version debits; **no double-spend**; loser succeeds on retry | Concurrent-conflict correctness demo |
| `read-balance.js` | Read path (Redis write-through cache) serves balances fast and error-free under steady load | Steady read-balance run |

### conflict.js in detail

1. Open a funded source + `N` distinct destinations.
2. `http.batch` fires `N` transfers **concurrently**, all debiting the source at the **same
   version**.
3. Assert **exactly one 201** and **N-1 conflicts**.
4. Poll the balance: source debited **exactly once** → `initial − amount` (the anti-double-spend
   proof).
5. Re-read the version, **retry the loser** → 201; balance now `initial − 2×amount` (retry applied
   exactly once).

Concurrency is inherently racy, so the batch is retried (fresh accounts) up to
`CONFLICT_MAX_ATTEMPTS` until a genuine single-winner conflict is surfaced — the demo is
deterministic without faking the race.

---

## Thresholds: k6-asserted vs. dashboard-observed

This split is the methodology signal. **k6 asserts only what a client can observe from HTTP
responses.** Everything server-internal is read off the CloudWatch ops dashboard
(`cloudledger-<env>`, from `terraform/modules/observability`).

### ✅ Asserted by k6 (`config/thresholds.js`)

| Threshold | Where |
|---|---|
| `http_req_duration{scenario:transfers}` **p99 < 200ms** (client view of the Aurora write) | load-transfers |
| `projection_lag` **p99 < 5000ms** (custom Trend; client-observed read-after-write — poll the projected balance until it reflects the write) | smoke |
| `checks` **rate == 100%** | smoke, conflict |
| `http_req_failed` **rate < 1%** | load; read path (scenario-tagged) |

### 📊 Observed on the CloudWatch dashboard — NOT asserted by k6 (HLD §12.1, server-side)

| Threshold | Why it's server-side |
|---|---|
| HikariCP connection-acquire **p99 < 2ms** | JVM pool metric; invisible to an HTTP client |
| GSI1 write-capacity **< 1% of base table** | DynamoDB CloudWatch metric |
| Redis cache-hit ratio **> 95%** | ElastiCache metric; a client can't see hit vs. miss |

Trying to assert these from k6 would be guessing. The suite deliberately draws the line at the
HTTP boundary and defers the rest to the dashboard.

---

## Contract prerequisites (current API vs. target contract)

The suite is written to the **target** W6 API contract. Where an element isn't in the API, the
suite handles it tolerantly and the gap is called out here so it's visible, not hidden:

| Target contract | Current API | How the suite handles it |
|---|---|---|
| `expected_version` in the transfer body (optimistic fence on the source debit) | Not a request field — the lock is enforced server-side by `events` `UNIQUE (aggregate_id, version)` | `lib/ledger.js` sends `expected_version` **only when supplied**; the race in `conflict.js` works either way because the lock is real |
| Concurrency conflict → **409** | ✅ **Fixed.** `GlobalExceptionHandler` now maps both `ConcurrencyException` (in-memory race) and the `uq_events_aggregate_version` `DataIntegrityViolationException` (true concurrent race that loses at the DB unique constraint) to **409** `{"error":"version_conflict"}` | `conflict.js` runs green with its default `CONFLICT_STATUS=409` — no override needed |

Note on the 409 fix: `saveAndFlush` means a *genuine* concurrent race loses at the DB unique
constraint (a `DataIntegrityViolationException`), **not** the in-memory `ConcurrencyException` —
so both are mapped. Only that specific constraint becomes a 409; any other integrity violation
still surfaces as 500. The anti-double-spend proof in `conflict.js` (exactly one debit; loser
retry succeeds) is independent of the status code and holds regardless.

---

## Guardrails (read before running load)

- **Dev sizing only.** Run against a local/dev target — never point a load generator at anything
  that shares capacity with real traffic.
- **Never leave a load generator running.** All load scenarios are time-boxed (drain stage
  included). After capturing screenshots/summaries, confirm the run has fully stopped — a
  forgotten arrival-rate run is a real cost leak.
- **Tear down after.** Stop the API / `bash terraform/scripts/local-destroy.sh` (local) once done.
- The `smoke.js` and `conflict.js` gates are cheap (1 VU) and safe to run in CI.

---

## Screenshot checklist (for the W6 write-up)

Capture these into `k6/results/` (gitignored):

- [ ] `smoke.js` terminal summary — all checks ✓, `projection_lag` p99 < 5s, exit code 0.
- [ ] `load-transfers.js` summary — `http_req_duration{scenario:transfers}` p99, `http_req_failed`
      rate, achieved req/s.
- [ ] `conflict.js` summary — the check lines showing **1 win / N-1 conflicts** and the successful
      loser retry (`conflict_wins` / `conflict_losses` counters).
- [ ] CloudWatch `cloudledger-<env>` dashboard during the load run — the **server-side** trio
      (HikariCP acquire, GSI1 WCU, Redis hit rate) + transfer-rate widget. This pairs with the k6
      client-side numbers to complete the §12.1 picture.

---

## CI/CD wiring — the two-tier standard

`k6 run k6/scenarios/smoke.js` exits **non-zero** on any failed threshold (failed check or
`projection_lag` p99 ≥ 5s), so it drops straight into a pipeline step. The suite is wired into CI
as **two tiers**, so a slow, costly load run never becomes a per-version merge/deploy blocker:

| Tier | Workflow | Trigger | Blocks release? |
|---|---|---|---|
| **Per-version gate** — `smoke.js` | [`.github/workflows/deploy-prod.yml`](../.github/workflows/deploy-prod.yml) | **automatic**, on tag `v*.*.*`, after the ECS rollout | **Yes** — a broken threshold fails the release |
| **Heavy load run** — `load-transfers.js` / `read-balance.js` | [`.github/workflows/k6-load.yml`](../.github/workflows/k6-load.yml) | **manual** (`workflow_dispatch`), human-gated | No — characterization run, not a gate |

### Per-version smoke gate (`deploy-prod.yml`)

The `k6 smoke gate (E2E)` step runs on every release right after `terraform apply` rolls ECS and
the version is verified. It targets the just-deployed prod ALB and pulls `BASE_URL` / `CLIENT_ID` /
`CLIENT_SECRET` from Terraform outputs (`TOKEN_URL` is the standard `cloudledger-prod` Cognito
domain). It reaches only **public** prod endpoints (ALB + Cognito), which is why it can run from a
GitHub-hosted runner even though Aurora/Flyway can't. The rollout has already happened, so a failed
gate doesn't auto-rollback — it surfaces a bad release (roll back by redeploying the prior tag).

### Heavy load run (`k6-load.yml`)

Manually dispatched with inputs (`target`, `scenario`, `target_tps`, `duration`, and a
`confirm: RUN` typed guard). Guardrails are baked into the workflow to honour the cost rules:

- `confirm: RUN` input → no accidental clicks
- `environment: load-test` → GitHub Environment approval gate + scoped secrets
- `timeout-minutes: 20` → hard cap enforcing "never leave a generator running"
- `concurrency` (no cancel) → never two overlapping runs

**Target config lives on the `load-test` GitHub Environment, not in prod Terraform** — point it at a
dev/sandbox stack you spin up for the run and tear down after. **Never at literal prod.** One-time
setup:

```bash
gh variable set K6_BASE_URL   --env load-test --body "http://<sandbox-alb-dns>"
gh variable set K6_TOKEN_URL  --env load-test --body "https://<sandbox>.auth.us-east-1.amazoncognito.com/oauth2/token"
gh variable set K6_CLIENT_ID  --env load-test --body "<m2m-client-id>"
gh secret   set K6_CLIENT_SECRET --env load-test --body "<m2m-client-secret>"
# optional: add a required-reviewers protection rule to the load-test environment for the approval gate
```

Both workflows install a **pinned k6 binary** (`K6_VERSION`, default `v0.55.0`) from the GitHub
release tarball rather than a third-party action, matching this repo's action SHA-pinning hygiene.
Bump the version in one place per workflow.
