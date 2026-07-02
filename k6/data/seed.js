import { SharedArray } from 'k6/data';

// Seed owners/accounts, parsed ONCE and shared read-only across every VU (SharedArray keeps
// a single copy in memory instead of one per VU).
//
// In this M2M setup every account is owned by the single Cognito client (the JWT `sub`), so
// "owner" here is a logical label used to spread seed data — accounts themselves are opened
// at runtime in each scenario's setup(). Override the seed set with SEED_JSON if desired.
export const seedOwners = new SharedArray('seed-owners', function () {
  if (__ENV.SEED_JSON) {
    return JSON.parse(__ENV.SEED_JSON);
  }
  return [
    { label: 'alice', currency: 'USD' },
    { label: 'bob', currency: 'USD' },
    { label: 'carol', currency: 'USD' },
    { label: 'dave', currency: 'USD' },
    { label: 'erin', currency: 'USD' },
  ];
});

export function seedCurrency(i) {
  return seedOwners[i % seedOwners.length].currency;
}
