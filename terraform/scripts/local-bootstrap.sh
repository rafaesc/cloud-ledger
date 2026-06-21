#!/usr/bin/env bash
# Full local environment bootstrap.
# Run once after cloning, or after a full reset (docker compose down).
set -euo pipefail

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566

REPO=localhost:5100/000000000000/us-east-1/cloudledger/outbox-poller
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "==> Starting Floci..."
docker compose -f "$REPO_ROOT/docker-compose.yml" up -d --wait

echo "==> Provisioning infrastructure..."
cd "$REPO_ROOT/terraform/envs/local"
terraform init -input=false
terraform apply -input=false -auto-approve

echo "==> Logging in to ECR registry..."
aws ecr get-login-password --endpoint-url "$AWS_ENDPOINT_URL" \
  | docker login --username AWS --password-stdin localhost:5100

echo "==> Building outbox-poller image..."
docker build \
  -t "$REPO:latest" \
  -f "$REPO_ROOT/lambdas/outbox_poller/Dockerfile" \
  "$REPO_ROOT/lambdas"

echo "==> Pushing image to Floci ECR..."
docker push "$REPO:latest"

echo ""
echo "Bootstrap complete. Run the API once to initialise Flyway migrations:"
echo "  cd api && ./gradlew bootRun"
