// Dependency-free UUID v4 generator (Math.random based). Deliberately avoids the remote
// jslib import (https://jslib.k6.io/...) so smoke.js stays runnable as a CI gate with no
// network access to fetch modules. Sufficient for Idempotency-Keys and account/transfer
// identifiers at load-test volumes; NOT for cryptographic use.
export function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
