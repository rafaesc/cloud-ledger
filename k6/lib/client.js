import http from 'k6/http';
import { uuidv4 } from './uuid.js';
import { environment } from '../config/environments.js';

// HTTP wrapper that centralises ALL cross-cutting write plumbing so scenario files stay
// declarative:
//   - injects `Authorization: Bearer <jwt>`
//   - injects a FRESH `Idempotency-Key: <uuid-v4>` on every write (unless the caller
//     supplies one to exercise the idempotency-replay contract)
//   - applies request tags so thresholds and Grafana can slice by scenario/operation
export function createClient(token, cfg) {
  cfg = cfg || environment();
  const baseUrl = cfg.baseUrl;

  function authHeaders(extra) {
    return Object.assign(
      { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      extra || {}
    );
  }

  function writeParams(opts) {
    const key = opts.idempotencyKey || uuidv4();
    return {
      key,
      params: {
        headers: authHeaders(Object.assign({ 'Idempotency-Key': key }, opts.headers)),
        tags: Object.assign({ operation: opts.operation || 'write' }, opts.tags || {}),
      },
    };
  }

  return {
    baseUrl,

    post(path, body, opts) {
      opts = opts || {};
      const { params } = writeParams(opts);
      return http.post(`${baseUrl}${path}`, JSON.stringify(body || {}), params);
    },

    get(path, opts) {
      opts = opts || {};
      return http.get(`${baseUrl}${path}`, {
        headers: authHeaders(opts.headers),
        tags: Object.assign({ operation: opts.operation || 'read' }, opts.tags || {}),
      });
    },

    // Build a request descriptor for http.batch() that carries JWT + a fresh Idempotency-Key.
    // Used by conflict.js to fire N writes truly concurrently from one VU.
    writeRequest(path, body, opts) {
      opts = opts || {};
      const { key, params } = writeParams(opts);
      return {
        method: 'POST',
        url: `${baseUrl}${path}`,
        body: JSON.stringify(body || {}),
        params,
        idempotencyKey: key,
      };
    },
  };
}
