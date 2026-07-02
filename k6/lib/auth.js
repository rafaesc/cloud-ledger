import http from 'k6/http';
import { environment } from '../config/environments.js';

// Cognito M2M (client-credentials grant) token fetch, with a module-level cache.
//
// k6 runs each VU in its own JS runtime, so this cache is per-VU. The scenarios call
// fetchToken() once in setup() and pass the token to VUs via the returned data object —
// that is the "fetch once, reuse everywhere" path. The cache still helps any code that
// calls fetchToken() again inside the same runtime.
let cachedToken = null;

export function fetchToken(cfg) {
  cfg = cfg || environment();
  if (cachedToken) return cachedToken;

  if (!cfg.clientId || !cfg.clientSecret) {
    throw new Error(
      'CLIENT_ID and CLIENT_SECRET env vars are required to fetch a Cognito token. ' +
      'See k6/README.md for how to pull them from Terraform state.'
    );
  }

  const res = http.post(
    cfg.tokenUrl,
    {
      grant_type: 'client_credentials',
      client_id: cfg.clientId,
      client_secret: cfg.clientSecret,
      scope: cfg.scope,
    },
    {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      tags: { operation: 'auth_token' },
    }
  );

  if (res.status !== 200) {
    throw new Error(`Cognito token fetch failed: ${res.status} ${res.body}`);
  }
  const token = res.json('access_token');
  if (!token) {
    throw new Error(`Cognito token response missing access_token: ${res.body}`);
  }
  cachedToken = token;
  return token;
}
