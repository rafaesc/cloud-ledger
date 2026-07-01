variable "env" {
  description = "Environment name (e.g. prod). Used for resource naming and tags."
  type        = string
}

variable "indexing_percentage" {
  description = "Percentage of ingested spans X-Ray indexes for Transaction Search (0-100). Traces are already head-sampled at the SDK, so 100 keeps every sampled trace searchable."
  type        = number
  default     = 1
}

# ── CloudWatch operations dashboard inputs ───────────────────────────────────

variable "aws_region" {
  description = "Region the dashboard metric widgets query."
  type        = string
  default     = "us-east-1"
}

variable "alb_arn_suffix" {
  description = "ALB ARN suffix (LoadBalancer dimension) for the p50/p99 latency widget."
  type        = string
}

variable "target_group_arn_suffix" {
  description = "Target group ARN suffix (TargetGroup dimension) for the latency widget."
  type        = string
}

variable "api_log_group_name" {
  description = "ECS API log group the transfer-rate metric filter attaches to."
  type        = string
}

variable "main_queue_name" {
  description = "Main SQS queue name — projection-lag widget reads its oldest-message age."
  type        = string
}

variable "dlq_name" {
  description = "DLQ name — DLQ-depth widget."
  type        = string
}

variable "redis_node_id" {
  description = "ElastiCache node id (CacheClusterId) for the Redis hit-rate widget."
  type        = string
}
