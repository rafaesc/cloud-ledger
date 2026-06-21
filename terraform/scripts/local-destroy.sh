#!/usr/bin/env bash
# Tear down the full local environment.
# Destroys all Terraform-managed resources, stops Floci, and wipes persisted state.
set -euo pipefail

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "==> Destroying Terraform-managed infrastructure..."
cd "$REPO_ROOT/terraform/envs/local"
terraform destroy -input=false -auto-approve

echo "==> Stopping Floci..."
docker compose -f "$REPO_ROOT/docker-compose.yml" down

echo "==> Wiping Floci persistent state..."
rm -rf "$REPO_ROOT/data"

echo "Destroy complete. Run local-bootstrap.sh to start fresh."
