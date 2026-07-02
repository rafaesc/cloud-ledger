// smoke.js — CI GATE. 1 VU × 10 transfers, end-to-end, on a fresh source→dest pair.
//
// Proves, per iteration: (a) a transfer is accepted (201), (b) the projected source balance
// reflects the debit within the freshness bound, and (c) projection lag < 5s. Setup also
// proves the idempotency-replay contract (same key ⇒ original response, no double-apply).
//
// Exits non-zero if any check fails or projection lag p99 ≥ 5s — wire it as a CI gate.
import { check } from 'k6';
import { environment } from '../config/environments.js';
import { fetchToken } from '../lib/auth.js';
import { createClient } from '../lib/client.js';
import { uuidv4 } from '../lib/uuid.js';
import * as ledger from '../lib/ledger.js';
import { recordProjectionLag } from '../lib/checks.js';
import * as T from '../config/thresholds.js';

const INITIAL_DEPOSIT = 100; // dollars
const TRANSFER_AMOUNT = 5; // dollars
const ITERATIONS = 10;
// End-to-end projection-freshness bound (ms), for the poll timeout AND the threshold. 5s suits
// dev AWS; raise it for local Floci, whose SQS→Lambda→DynamoDB projection can take tens of seconds
// (the e2e suite allows 60s), e.g. -e PROJECTION_LAG_MS=60000.
const PROJECTION_LAG_MS = Number(__ENV.PROJECTION_LAG_MS || 5000);

export const options = {
  scenarios: {
    smoke: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: ITERATIONS,
      maxDuration: '3m',
    },
  },
  thresholds: T.merge(T.allChecksPass, T.projectionLag),
};

export function setup() {
  const cfg = environment();
  const token = fetchToken(cfg);
  const client = createClient(token, cfg);

  const src = ledger.openAccount(client, { currency: cfg.currency });
  const dst = ledger.openAccount(client, { currency: cfg.currency });
  check(src.res, { 'source account opened (201)': (r) => r.status === 201 });
  check(dst.res, { 'dest account opened (201)': (r) => r.status === 201 });

  // Fund the source, then REPLAY the same deposit with the same Idempotency-Key: the contract
  // says the replay returns the original response and does NOT apply a second time.
  const key = uuidv4();
  const first = ledger.deposit(client, src.accountId, INITIAL_DEPOSIT, { idempotencyKey: key });
  const replay = ledger.deposit(client, src.accountId, INITIAL_DEPOSIT, { idempotencyKey: key });
  check(first, { 'initial deposit (201)': (r) => r.status === 201 });
  check(replay, { 'idempotent replay returns original status': (r) => r.status === first.status });

  // Balance must reflect exactly ONE deposit (allow a generous warmup for cold projection).
  const funded = ledger.pollBalance(client, src.accountId, INITIAL_DEPOSIT * 100, {
    timeoutMs: Math.max(15000, PROJECTION_LAG_MS),
  });
  check(funded, { 'idempotent replay did not double-apply the deposit': (p) => p.matched });

  return {
    token,
    srcAccountId: src.accountId,
    dstAccountId: dst.accountId,
    startingSrcCents: INITIAL_DEPOSIT * 100,
  };
}

// Per-VU running expectation (vus=1, so a single VU owns the whole ledger of transfers).
let expectedSrcCents = null;

export default function (data) {
  const client = createClient(data.token);
  if (expectedSrcCents === null) expectedSrcCents = data.startingSrcCents;

  const res = ledger.transfer(client, {
    sourceAccountId: data.srcAccountId,
    destinationAccountId: data.dstAccountId,
    amount: TRANSFER_AMOUNT,
  });
  check(res, { 'transfer accepted (201)': (r) => r.status === 201 });
  if (res.status === 201) {
    expectedSrcCents -= TRANSFER_AMOUNT * 100;
  }

  // E2E freshness: the projected source balance must catch up to the new value within the bound.
  const poll = ledger.pollBalance(client, data.srcAccountId, expectedSrcCents, {
    timeoutMs: PROJECTION_LAG_MS,
  });
  recordProjectionLag(poll.elapsedMs);
  check(poll, {
    'source balance reflects transfer within bound': (p) => p.matched,
    'projection lag within bound': (p) => p.elapsedMs < PROJECTION_LAG_MS,
  });
}

export function teardown(data) {
  // The destination must have received every transfer (sum of all iterations).
  const client = createClient(data.token);
  const expectedDstCents = TRANSFER_AMOUNT * 100 * ITERATIONS;
  const poll = ledger.pollBalance(client, data.dstAccountId, expectedDstCents, {
    timeoutMs: Math.max(10000, PROJECTION_LAG_MS),
  });
  check(poll, { 'destination balance equals sum of all transfers': (p) => p.matched });
}
