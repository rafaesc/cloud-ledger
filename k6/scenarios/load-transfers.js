// load-transfers.js — the MODEST load run. Ramping-arrival-rate to ~300 req/s of transfers
// over a few minutes, exercising the Aurora write path under sustained concurrency.
//
// Asserts the client-observable subset:
//   - http_req_duration{scenario:transfers} p99 < 200ms
//   - http_req_failed rate < 1%
// Server-side thresholds (HikariCP acquire, GSI1 WCU, Redis hit-rate) are read off the
// CloudWatch dashboard, not here — see README.
import { check } from 'k6';
import exec from 'k6/execution';
import { environment } from '../config/environments.js';
import { fetchToken } from '../lib/auth.js';
import { createClient } from '../lib/client.js';
import * as ledger from '../lib/ledger.js';
import { seedCurrency } from '../data/seed.js';
import * as T from '../config/thresholds.js';

const POOL_SIZE = Number(__ENV.POOL_SIZE || 100); // total accounts; split source/sink
const TARGET_RPS = Number(__ENV.TARGET_RPS || 300);
const TRANSFER_AMOUNT = 1; // dollars
const POOL_DEPOSIT = 100000; // dollars — large enough to never run dry over a few-minute run

export const options = {
  // Scenario key is `transfers` so the built-in `scenario` tag matches the
  // http_req_duration{scenario:transfers} threshold with no manual tagging.
  scenarios: {
    transfers: {
      executor: 'ramping-arrival-rate',
      startRate: 20,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 400),
      stages: [
        { target: TARGET_RPS, duration: __ENV.RAMP || '1m' },
        { target: TARGET_RPS, duration: __ENV.HOLD || '2m' },
        { target: 0, duration: '30s' },
      ],
    },
  },
  thresholds: T.merge(T.transferLatency, T.loadErrorBudget),
};

export function setup() {
  const cfg = environment();
  const token = fetchToken(cfg);
  const client = createClient(token, cfg);

  // Disjoint source/sink pools so a debit and a credit never contend on the same aggregate's
  // version. Optimistic-lock CONTENTION is exercised deliberately in conflict.js; mixing it in
  // here would inflate http_req_failed with expected conflicts and muddy the latency signal.
  const sources = [];
  const sinks = [];
  const half = Math.max(1, Math.floor(POOL_SIZE / 2));
  for (let i = 0; i < half; i++) {
    const a = ledger.openAccount(client, { currency: seedCurrency(i), tags: { operation: 'seed_open' } });
    ledger.deposit(client, a.accountId, POOL_DEPOSIT, { tags: { operation: 'seed_deposit' } });
    sources.push(a.accountId);
    const b = ledger.openAccount(client, { currency: seedCurrency(i), tags: { operation: 'seed_open' } });
    sinks.push(b.accountId);
  }
  return { token, sources, sinks };
}

export default function (data) {
  const client = createClient(data.token);
  const i = exec.scenario.iterationInTest;
  const src = data.sources[i % data.sources.length];
  const dst = data.sinks[i % data.sinks.length];

  const res = ledger.transfer(client, {
    sourceAccountId: src,
    destinationAccountId: dst,
    amount: TRANSFER_AMOUNT,
  });
  check(res, { 'transfer accepted (201)': (r) => r.status === 201 });
}
