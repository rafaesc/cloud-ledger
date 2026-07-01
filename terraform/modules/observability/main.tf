# ── X-Ray Transaction Search (account-level) ─────────────────────────────────
# The X-Ray OTLP endpoint (https://xray.<region>.amazonaws.com/v1/traces) only ingests spans once
# Transaction Search is enabled for the account. There is no native terraform-aws resource for this
# yet, so we wrap the AWS-documented CloudFormation directly:
#   1. AWS::Logs::ResourcePolicy         — lets the X-Ray service write spans into the aws/spans log group.
#   2. AWS::XRay::TransactionSearchConfig — flips the account into Transaction Search (CloudWatch Logs)
#                                           ingestion mode and sets how many spans are indexed.
# Fn::Sub resolves AWS::Partition / Region / AccountId at deploy time, so no TF interpolation needed.
#
# NOTE: account-level + singleton. CloudFormation requires Transaction Search to be DISABLED before
# this stack creates it; if it is already on, import or skip this module.
resource "aws_cloudformation_stack" "xray_transaction_search" {
  name = "cloudledger-${var.env}-xray-transaction-search"

  template_body = jsonencode({
    Resources = {
      LogsResourcePolicy = {
        Type = "AWS::Logs::ResourcePolicy"
        Properties = {
          PolicyName = "TransactionSearchAccess"
          # Fn::Sub so CloudFormation expands the AWS::Partition / Region / AccountId pseudo-params.
          PolicyDocument = {
            "Fn::Sub" = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"TransactionSearchXRayAccess\",\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"xray.amazonaws.com\"},\"Action\":\"logs:PutLogEvents\",\"Resource\":[\"arn:$${AWS::Partition}:logs:$${AWS::Region}:$${AWS::AccountId}:log-group:aws/spans:*\",\"arn:$${AWS::Partition}:logs:$${AWS::Region}:$${AWS::AccountId}:log-group:/aws/application-signals/data:*\"],\"Condition\":{\"ArnLike\":{\"aws:SourceArn\":\"arn:$${AWS::Partition}:xray:$${AWS::Region}:$${AWS::AccountId}:*\"},\"StringEquals\":{\"aws:SourceAccount\":\"$${AWS::AccountId}\"}}}]}"
          }
        }
      }
      XRayTransactionSearchConfig = {
        Type      = "AWS::XRay::TransactionSearchConfig"
        DependsOn = "LogsResourcePolicy"
        Properties = {
          IndexingPercentage = var.indexing_percentage
        }
      }
    }
  })

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# ── Transfer-rate metric ─────────────────────────────────────────────────────
# The API is instrumented for tracing (X-Ray), not CloudWatch metrics, so there is no native
# "transfers" metric. TransferController logs a `TRANSFER_COMPLETED` marker on every committed
# transfer; this filter turns that log line into a countable metric the dashboard graphs.
locals {
  metrics_namespace = "CloudLedger/${var.env}"
}

resource "aws_cloudwatch_log_metric_filter" "transfers_completed" {
  name           = "cloudledger-${var.env}-transfers-completed"
  log_group_name = var.api_log_group_name
  pattern        = "TRANSFER_COMPLETED"

  metric_transformation {
    name          = "TransfersCompleted"
    namespace     = local.metrics_namespace
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

# ── Operations dashboard ─────────────────────────────────────────────────────
# Five widgets: transfer rate, p50/p99 API latency, projection lag (SQS oldest-message age),
# DLQ depth, and Redis hit rate. Latency/lag/DLQ/Redis come from native AWS service metrics;
# transfer rate comes from the log metric filter above.
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "cloudledger-${var.env}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "Transfer rate (transfers/min)"
          region  = var.aws_region
          view    = "timeSeries"
          stacked = false
          period  = 60
          metrics = [
            [local.metrics_namespace, "TransfersCompleted", { stat = "Sum", label = "transfers/min" }]
          ]
          yAxis = { left = { min = 0 } }
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "API latency (ALB TargetResponseTime)"
          region = var.aws_region
          view   = "timeSeries"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix, { stat = "p50", label = "p50" }],
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix, { stat = "p99", label = "p99" }]
          ]
          yAxis = { left = { min = 0, label = "seconds", showUnits = false } }
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "Projection lag (oldest unprocessed event)"
          region = var.aws_region
          view   = "timeSeries"
          period = 60
          metrics = [
            ["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", var.main_queue_name, { stat = "Maximum", label = "lag (s)" }],
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", var.main_queue_name, { stat = "Maximum", label = "backlog (msgs)", yAxis = "right" }]
          ]
          yAxis = {
            left  = { min = 0, label = "seconds", showUnits = false }
            right = { min = 0, label = "messages", showUnits = false }
          }
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "DLQ depth"
          region = var.aws_region
          view   = "timeSeries"
          period = 60
          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", var.dlq_name, { stat = "Maximum", label = "messages in DLQ" }]
          ]
          yAxis = { left = { min = 0 } }
          annotations = {
            horizontal = [
              { label = "any DLQ message", value = 1, color = "#d13212" }
            ]
          }
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 6
        width  = 8
        height = 6
        properties = {
          title  = "Redis cache hit rate"
          region = var.aws_region
          view   = "timeSeries"
          period = 300
          metrics = [
            ["AWS/ElastiCache", "CacheHits", "CacheClusterId", var.redis_node_id, { id = "hits", stat = "Sum", visible = false }],
            ["AWS/ElastiCache", "CacheMisses", "CacheClusterId", var.redis_node_id, { id = "misses", stat = "Sum", visible = false }],
            [{ expression = "100 * hits / (hits + misses)", label = "hit rate %", id = "hitrate" }]
          ]
          yAxis = { left = { min = 0, max = 100, label = "percent", showUnits = false } }
        }
      }
    ]
  })
}
