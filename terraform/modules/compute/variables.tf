variable "env" {
  description = "Environment name (local, prod)"
  type        = string
}

variable "subnet_ids" {
  description = "Private subnet IDs for Lambda VPC config"
  type        = list(string)
}

variable "lambda_sg_id" {
  description = "Security group ID for Lambda, from the networking module"
  type        = string
}

variable "queue_url" {
  description = "SQS queue URL passed to Lambda as env var"
  type        = string
}

variable "queue_arn" {
  description = "SQS queue ARN used in the Lambda IAM policy"
  type        = string
}

variable "db_host" {
  description = "RDS host address, from the storage module"
  type        = string
}

variable "db_port" {
  description = "RDS port"
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "Database name, from the storage module"
  type        = string
}

variable "db_user" {
  description = "Database username"
  type        = string
}

variable "db_password" {
  description = "Database password"
  type        = string
  sensitive   = true
}

variable "sqs_endpoint_url" {
  description = "SQS endpoint URL — overridden in local to point at Floci"
  type        = string
}
