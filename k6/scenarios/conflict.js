// conflict.js — ⭐ CORRECTNESS demo, not a throughput test.
//
// Fires N transfers CONCURRENTLY (one http.batch) that all debit the SAME source account at
// the SAME starting version. Optimistic locking must let exactly ONE win (201) and reject the
// rest as conflicts. Then it re-reads the version, retries the loser, and proves the retry
// succeeds — with the source debited exactly ONCE per successful transfer (no double-spend).
//
// Why a retry loop around the batch: true concurrency is inherently racy. If the server
// happens to serialise the batch (0 or >1 winners) we retry with fresh accounts until a
// genuine single-winner conflict is surfaced, so the demo is deterministic.
//
// ── CONTRACT NOTE (loser-status check) ───────────────────────────────────────────────────
// The HLD/CLAUDE.md invariant is "conflicts surface as 409", and GlobalExceptionHandler now
// honours it: both the in-memory ConcurrencyException and the uq_events_aggregate_version
// DataIntegrityViolationException (the true-concurrent loser at the DB unique constraint) map
// to 409. So the default CONFLICT_STATUS=409 is correct against the current build.
// CONFLICT_STATUS remains overridable (e.g. -e CONFLICT_STATUS=500) only for demoing against an
// older build that predates that mapping. The core anti-double-spend proof (exactly one debit;
// loser retry succeeds) is independent of the status code.
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { environment } from '../config/environments.js';
import { fetchToken } from '../lib/auth.js';
import { createClient } from '../lib/client.js';
import * as ledger from '../lib/ledger.js';
import { conflictWins, conflictLosses, recordProjectionLag } from '../lib/checks.js';
import * as T from '../config/thresholds.js';

const N = Number(__ENV.CONFLICT_N || 2); // simultaneous transfers on ONE source
const CONFLICT_STATUS = Number(__ENV.CONFLICT_STATUS || 409); // target contract; see note above
const MAX_RACE_ATTEMPTS = Number(__ENV.CONFLICT_MAX_ATTEMPTS || 10);
const INITIAL = 1000; // dollars
const AMOUNT = 10; // dollars per transfer
// How long the post-commit balance polls wait for projection. Raise for local Floci, whose
// projection can take tens of seconds (e.g. -e PROJECTION_LAG_MS=60000).
const PROJECTION_LAG_MS = Number(__ENV.PROJECTION_LAG_MS || 8000);

export const options = {
  scenarios: {
    conflict: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '3m' },
  },
  thresholds: T.merge(T.allChecksPass),
};

export function setup() {
  const cfg = environment();
  return { token: fetchToken(cfg), currency: cfg.currency };
}

// Open a funded source + N distinct destinations, then fire N concurrent same-version debits.
// Returns the single-winner race outcome, or null if it serialised this attempt.
function runRace(client, currency) {
  const src = ledger.openAccount(client, { currency });
  check(src.res, { 'conflict source opened (201)': (r) => r.status === 201 });
  ledger.deposit(client, src.accountId, INITIAL, {});

  const entries = [];
  for (let i = 0; i < N; i++) {
    const d = ledger.openAccount(client, { currency });
    const req = ledger.transferRequest(
      client,
      { sourceAccountId: src.accountId, destinationAccountId: d.accountId, amount: AMOUNT },
      { tags: { phase: 'race' } }
    );
    entries.push({ dst: d.accountId, req, transferId: JSON.parse(req.body).transferId });
  }

  const responses = http.batch(entries.map((e) => e.req));
  const results = entries.map((e, idx) => ({
    dst: e.dst,
    transferId: e.transferId,
    status: responses[idx].status,
  }));
  const wins = results.filter((r) => r.status === 201);
  const losses = results.filter((r) => r.status !== 201);

  if (wins.length === 1 && losses.length === N - 1) {
    return { sourceId: src.accountId, wins, losses };
  }
  return null; // serialised — caller retries with fresh accounts
}

export default function (data) {
  const client = createClient(data.token);

  let outcome = null;
  for (let attempt = 0; attempt < MAX_RACE_ATTEMPTS && !outcome; attempt++) {
    outcome = runRace(client, data.currency);
    if (!outcome) sleep(0.2);
  }
  if (!outcome) {
    fail(
      `could not surface a single-winner conflict after ${MAX_RACE_ATTEMPTS} attempts ` +
      `(server serialised the concurrent batch every time)`
    );
  }

  conflictWins.add(outcome.wins.length);
  conflictLosses.add(outcome.losses.length);

  const initialCents = INITIAL * 100;

  // ── Correctness: exactly one winner, the rest are conflicts ──────────────────────────────
  check(outcome, {
    'exactly one transfer won (201)': (o) => o.wins.length === 1,
    [`exactly ${N - 1} transfer(s) lost to the version conflict`]: (o) => o.losses.length === N - 1,
    [`loser status is ${CONFLICT_STATUS} (conflict contract)`]: (o) =>
      o.losses.every((r) => r.status === CONFLICT_STATUS),
  });

  // ── Anti-double-spend: only ONE debit applied ⇒ balance == initial − AMOUNT ───────────────
  const afterOne = ledger.pollBalance(client, outcome.sourceId, initialCents - AMOUNT * 100, {
    timeoutMs: PROJECTION_LAG_MS,
  });
  recordProjectionLag(afterOne.elapsedMs);
  check(afterOne, { 'source debited exactly once (no double-spend)': (p) => p.matched });

  // ── Retry the loser: re-read version, resubmit, must now succeed ─────────────────────────
  const bal = ledger.getBalance(client, outcome.sourceId, {});
  const currentVersion = bal.status === 200 ? bal.json('version') : undefined;
  const loser = outcome.losses[0];
  const retry = ledger.transfer(
    client,
    {
      sourceAccountId: outcome.sourceId,
      destinationAccountId: loser.dst,
      transferId: loser.transferId, // same logical transfer, fresh Idempotency-Key auto-injected
      amount: AMOUNT,
      expectedVersion: currentVersion, // sent only if defined (target contract)
    },
    { tags: { phase: 'retry' } }
  );
  check(retry, { 'loser retry succeeds (201)': (r) => r.status === 201 });

  // Two successful debits now ⇒ balance == initial − 2×AMOUNT (retry applied exactly once).
  const afterTwo = ledger.pollBalance(client, outcome.sourceId, initialCents - 2 * AMOUNT * 100, {
    timeoutMs: PROJECTION_LAG_MS,
  });
  recordProjectionLag(afterTwo.elapsedMs);
  check(afterTwo, { 'retry applied exactly once (balance = initial − 2×amount)': (p) => p.matched });
}
