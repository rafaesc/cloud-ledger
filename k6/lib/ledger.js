import { sleep } from 'k6';
import { uuidv4 } from './uuid.js';

// Domain helpers — the ONLY place scenario files touch CloudLedger's REST contract.
// Amounts are whole dollars (BigDecimal on the wire); balances come back as integer cents
// (dollars × 100) in `balance_cents`.

const DEFAULT_CURRENCY = 'USD';

export function openAccount(client, opts) {
  opts = opts || {};
  const accountId = opts.accountId || uuidv4();
  const res = client.post(
    '/v1/accounts',
    { accountId, currency: opts.currency || DEFAULT_CURRENCY },
    { operation: 'open_account', tags: opts.tags, idempotencyKey: opts.idempotencyKey }
  );
  return { accountId, res };
}

export function deposit(client, accountId, amount, opts) {
  opts = opts || {};
  return client.post(
    `/v1/accounts/${accountId}/deposits`,
    { amount },
    { operation: 'deposit', tags: opts.tags, idempotencyKey: opts.idempotencyKey }
  );
}

export function withdraw(client, accountId, amount, opts) {
  opts = opts || {};
  return client.post(
    `/v1/accounts/${accountId}/withdrawals`,
    { amount },
    { operation: 'withdraw', tags: opts.tags, idempotencyKey: opts.idempotencyKey }
  );
}

function transferBody(params) {
  const body = {
    sourceAccountId: params.sourceAccountId,
    destinationAccountId: params.destinationAccountId,
    amount: params.amount,
    transferId: params.transferId || uuidv4(),
  };
  // expected_version is part of the TARGET contract: an optimistic-lock fence on the SOURCE
  // account's debit. It is sent only when supplied. The current API enforces the same lock
  // implicitly via the events(aggregate_id, version) unique constraint, so an unset value is
  // still safe. See README "Contract prerequisites".
  if (params.expectedVersion !== undefined && params.expectedVersion !== null) {
    body.expected_version = params.expectedVersion;
  }
  return body;
}

export function transfer(client, params, opts) {
  opts = opts || {};
  return client.post('/v1/transfers', transferBody(params), {
    operation: 'transfer',
    tags: opts.tags,
    idempotencyKey: opts.idempotencyKey,
  });
}

// http.batch() request descriptor variant, for firing concurrent transfers (conflict.js).
export function transferRequest(client, params, opts) {
  opts = opts || {};
  return client.writeRequest('/v1/transfers', transferBody(params), {
    operation: 'transfer',
    tags: opts.tags,
    idempotencyKey: opts.idempotencyKey,
  });
}

export function getBalance(client, accountId, opts) {
  opts = opts || {};
  return client.get(`/v1/accounts/${accountId}/balance`, {
    operation: 'get_balance',
    tags: opts.tags,
  });
}

export function getTransactions(client, accountId, opts) {
  opts = opts || {};
  const q = [];
  if (opts.limit) q.push(`limit=${opts.limit}`);
  if (opts.cursor) q.push(`cursor=${encodeURIComponent(opts.cursor)}`);
  const qs = q.length ? `?${q.join('&')}` : '';
  return client.get(`/v1/accounts/${accountId}/transactions${qs}`, {
    operation: 'get_transactions',
    tags: opts.tags,
  });
}

// Poll GET /balance until balance_cents === expectedCents or the timeout elapses.
// Returns { matched, elapsedMs, balanceCents, res }. `elapsedMs` doubles as a client-observed
// read-after-write projection-lag measurement.
export function pollBalance(client, accountId, expectedCents, opts) {
  opts = opts || {};
  const timeoutMs = opts.timeoutMs || 5000;
  const intervalMs = opts.intervalMs || 200;
  const start = Date.now();
  let balanceCents = null;
  let res = null;
  while (Date.now() - start < timeoutMs) {
    res = getBalance(client, accountId, { tags: opts.tags });
    if (res.status === 200) {
      balanceCents = res.json('balance_cents');
      if (Number(balanceCents) === Number(expectedCents)) {
        return { matched: true, elapsedMs: Date.now() - start, balanceCents, res };
      }
    }
    sleep(intervalMs / 1000);
  }
  return { matched: false, elapsedMs: Date.now() - start, balanceCents, res };
}
