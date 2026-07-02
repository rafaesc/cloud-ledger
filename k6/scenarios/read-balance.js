// read-balance.js — steady read load. constant-arrival-rate GET /balance against a warm
// account whose balance was populated via the Redis write-through on deposit.
//
// The assertions here are the client-observable read path: latency and failure rate. This is a
// pure read with no read-after-write baseline, so it does not measure or gate projection lag —
// end-to-end projection freshness is gated in smoke.js.
import { check } from 'k6';
import { environment } from '../config/environments.js';
import { fetchToken } from '../lib/auth.js';
import { createClient } from '../lib/client.js';
import * as ledger from '../lib/ledger.js';
import * as T from '../config/thresholds.js';

const TARGET_RPS = Number(__ENV.READ_RPS || 200);
const DEPOSIT = 500; // dollars

export const options = {
  scenarios: {
    reads: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RPS,
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 200),
    },
  },
  thresholds: T.readPath,
};

export function setup() {
  const cfg = environment();
  const token = fetchToken(cfg);
  const client = createClient(token, cfg);

  // One warm account. The deposit write-through primes the Redis balance cache, so reads
  // during the run should hit the hot path.
  const a = ledger.openAccount(client, { currency: cfg.currency });
  ledger.deposit(client, a.accountId, DEPOSIT, {});
  // Wait for the first projection so reads don't 404 at the start of the run. Raise
  // PROJECTION_LAG_MS for local Floci, whose first projection (cold Lambda) can be slow.
  const warmupMs = Math.max(15000, Number(__ENV.PROJECTION_LAG_MS || 0));
  ledger.pollBalance(client, a.accountId, DEPOSIT * 100, { timeoutMs: warmupMs });

  return { token, accountId: a.accountId };
}

export default function (data) {
  const client = createClient(data.token);
  const res = ledger.getBalance(client, data.accountId, {});
  check(res, {
    'balance read 200': (r) => r.status === 200,
    'balance body present': (r) => r.status === 200 && r.json('balance_cents') !== undefined,
  });
}
