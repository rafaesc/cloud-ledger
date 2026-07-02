import { Trend, Counter } from 'k6/metrics';

// Projection freshness in milliseconds — client-observed read-after-write lag: the time from a
// committed write until the projected read model reflects it (measured by polling the balance in
// smoke.js / conflict.js). read-balance.js is a pure read and does not feed this.
export const projectionLagTrend = new Trend('projection_lag', true);

// Correctness counters for the conflict demo.
export const conflictWins = new Counter('conflict_wins');
export const conflictLosses = new Counter('conflict_losses');

// Record a client-observed projection-lag sample (ms). No-op for null/undefined.
export function recordProjectionLag(observedMs) {
  if (observedMs !== undefined && observedMs !== null) {
    projectionLagTrend.add(observedMs);
  }
}
