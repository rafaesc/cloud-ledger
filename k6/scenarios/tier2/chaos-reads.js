// ─────────────────────────────────────────────────────────────────────────────
// DEFERRED — DO NOT RUN. Tier-2 deliverable, out of scope for the W6 Tier-1 slice.
// TODO: steady read load under AWS FIS chaos (kill a Redis node / inject Aurora failover)
//       and assert the balance-cache circuit breaker degrades reads to the DynamoDB
//       fallback within SLO instead of cascading timeouts.
// ─────────────────────────────────────────────────────────────────────────────
import { fail } from 'k6';

export const options = { scenarios: {} };

export default function () {
  fail('tier2/chaos-reads.js is a DEFERRED stub — see k6/README.md "Tier-2 (deferred)". Do not run.');
}
