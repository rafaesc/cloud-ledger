// ─────────────────────────────────────────────────────────────────────────────
// DEFERRED — DO NOT RUN. Tier-2 deliverable, out of scope for the W6 Tier-1 slice.
// TODO: 5,000 TPS / 2-hour soak with hot-account injection at scale, driven from the
//       ECS Fargate Spot k6 fleet (distributed load), not a single local generator.
// ─────────────────────────────────────────────────────────────────────────────
import { fail } from 'k6';

export const options = { scenarios: {} };

export default function () {
  fail('tier2/soak.js is a DEFERRED stub — see k6/README.md "Tier-2 (deferred)". Do not run.');
}
