#!/usr/bin/env bash
# Import existing Floci resources into Terraform state.
# Use this when a previous apply failed mid-way and left orphaned resources.
# Networking resources (VPC, subnets, security groups) have dynamic IDs and
# are not covered — if those are orphaned, run local-destroy.sh instead.
set -euo pipefail

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../envs/local"

# Imports a resource only if it is not already tracked in state.
try_import() {
  local address=$1
  local id=$2
  if terraform state show "$address" &>/dev/null; then
    echo "  skip (already in state): $address"
  else
    echo "  importing: $address <- $id"
    terraform import -input=false "$address" "$id"
  fi
}

echo "==> Importing messaging resources..."
QUEUE_URL=$(aws sqs get-queue-url \
  --queue-name cloudledger-events \
  --query QueueUrl --output text 2>/dev/null || true)
if [ -n "$QUEUE_URL" ]; then
  try_import "module.messaging.aws_sqs_queue.main" "$QUEUE_URL"
else
  echo "  skip (not found in Floci): aws_sqs_queue cloudledger-events"
fi

echo "==> Importing storage resources..."
try_import "module.storage.aws_db_instance.main"          "cloudledger-postgres"
try_import "module.storage.aws_dynamodb_table.projections" "cloudledger-projections"

echo "==> Importing compute resources..."
try_import "module.compute.aws_ecr_repository.main"              "cloudledger/outbox-poller"
try_import "module.compute.aws_ecr_repository.projector"         "cloudledger/projector"
try_import "module.compute.aws_iam_role.lambda"                  "cloudledger-local-lambda"
try_import "module.compute.aws_iam_role.scheduler"               "cloudledger-local-scheduler"
try_import "module.compute.aws_lambda_function.main"             "outbox-poller"
try_import "module.compute.aws_lambda_function.projector"        "projector"
try_import "module.compute.aws_scheduler_schedule_group.main"    "cloudledger-local"
try_import "module.compute.aws_scheduler_schedule.main"          "cloudledger-local/outbox-poller"

ESM_UUID=$(aws lambda list-event-source-mappings \
  --function-name projector \
  --query "EventSourceMappings[0].UUID" \
  --output text 2>/dev/null || true)
if [ -n "$ESM_UUID" ] && [ "$ESM_UUID" != "None" ]; then
  try_import "module.compute.aws_lambda_event_source_mapping.sqs_to_projector" "$ESM_UUID"
else
  echo "  skip (not found in Floci): aws_lambda_event_source_mapping sqs_to_projector"
fi

echo ""
echo "Import complete. Run 'terraform apply' to reconcile any drift."
