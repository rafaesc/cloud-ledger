// Per-environment configuration. Nothing is hardcoded to a real deployment — every value
// is read from __ENV so the same scripts run against local Floci or dev AWS unchanged.
// Defaults target a local Floci stack (ALB on :80, Cognito on :4566).

const DEFAULTS = {
  BASE_URL: 'http://localhost',
  TOKEN_URL: 'http://localhost:4566/cognito-idp/oauth2/token',
  SCOPE: 'https://api.getcloudledger.com/write https://api.getcloudledger.com/read',
  CURRENCY: 'USD',
};

export function env(name, fallback) {
  const v = __ENV[name];
  return v !== undefined && v !== '' ? v : fallback;
}

// Resolve the active environment from __ENV. CLIENT_ID / CLIENT_SECRET are required for the
// Cognito client-credentials grant (see README for how to pull them from Terraform state).
export function environment() {
  return {
    baseUrl: env('BASE_URL', DEFAULTS.BASE_URL).replace(/\/+$/, ''),
    tokenUrl: env('TOKEN_URL', DEFAULTS.TOKEN_URL),
    clientId: env('CLIENT_ID', ''),
    clientSecret: env('CLIENT_SECRET', ''),
    scope: env('SCOPE', DEFAULTS.SCOPE),
    currency: env('CURRENCY', DEFAULTS.CURRENCY),
  };
}
