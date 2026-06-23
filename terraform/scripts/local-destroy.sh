#!/usr/bin/env bash
# Tear down the full local environment.
# Destroys all Terraform-managed resources, stops Floci, and wipes persisted state.
set -euo pipefail

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$REPO_ROOT/terraform/envs/local"

# Floci returns HTTP 200 for DeleteListener and DeleteLoadBalancer but with a malformed
# XML body — Terraform can't parse the response and fails even though the deletes succeed.
# Floci also deletes RDS instances asynchronously, so DeleteDBCluster fires before the
# instance is truly gone. All of these get wiped by docker compose down + rm -rf data,
# so removing them from state before destroy is safe and avoids the spurious errors.
echo "==> Removing Floci-incompatible resources from Terraform state..."
terraform state rm 'module.compute.aws_lb_listener.http'           2>/dev/null || true
terraform state rm 'module.compute.aws_lb_target_group.api'        2>/dev/null || true
terraform state rm 'module.compute.aws_lb.main'                    2>/dev/null || true
terraform state rm 'module.storage.aws_rds_cluster_instance.writer' 2>/dev/null || true
terraform state rm 'module.storage.aws_rds_cluster.main'           2>/dev/null || true

echo "==> Destroying Terraform-managed infrastructure..."
terraform destroy -input=false -auto-approve

echo "==> Stopping Floci..."
docker compose -f "$REPO_ROOT/docker-compose.yml" down

echo "==> Removing Floci ECR sidecar and its data volume..."
docker rm -f floci-ecr-registry 2>/dev/null || true
docker volume rm floci-ecr-registry-data 2>/dev/null || true

echo "==> Wiping Floci persistent state..."
rm -rf "$REPO_ROOT/data"

echo "Destroy complete. Run local-bootstrap.sh to start fresh."
