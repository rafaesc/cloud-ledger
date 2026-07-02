// ─────────────────────────────────────────────────────────────────────────────
// k6-OBSERVABLE thresholds ONLY — the client-side subset of HLD §12.1.
//
// The remaining HLD §12.1 thresholds are SERVER-SIDE and are read off the CloudWatch
// ops dashboard (modules/observability), NOT asserted here:
//   - HikariCP connection-acquire p99 < 2ms
//   - GSI1 write-capacity (WCU) < 1% of base table
//   - Redis cache-hit ratio > 95%
// This "k6 asserts client-observable / CloudWatch owns server-side" split is deliberate —
// see README "k6-asserted vs dashboard-observed". It is the methodology signal.
// ─────────────────────────────────────────────────────────────────────────────

// Transfer write path — client view of the Aurora write latency.
export const transferLatency = {
  'http_req_duration{scenario:transfers}': ['p(99)<200'],
};

// Projection freshness (custom Trend): client-observed read-after-write lag — poll the projected
// balance until it reflects the write. Gated in smoke.js at the W6 DoD end-to-end bound (< 5s on
// dev AWS). Raise PROJECTION_LAG_MS for slower environments — local Floci projection can take tens
// of seconds (the e2e suite allows 60s), e.g. -e PROJECTION_LAG_MS=60000.
const PROJECTION_LAG_MS = Number(__ENV.PROJECTION_LAG_MS || 5000);
export const projectionLag = {
  projection_lag: [`p(99)<${PROJECTION_LAG_MS}`],
};

// Load run: overall error budget.
export const loadErrorBudget = {
  http_req_failed: ['rate<0.01'],
};

// Read path: fast cache reads, near-zero failures.
export const readPath = {
  'http_req_failed{scenario:reads}': ['rate<0.01'],
  'http_req_duration{scenario:reads}': ['p(99)<100'],
};

// Correctness gates (smoke, conflict): every check must pass or the run exits non-zero.
export const allChecksPass = {
  checks: ['rate==1.0'],
};

export function merge(...objs) {
  return Object.assign({}, ...objs);
}
