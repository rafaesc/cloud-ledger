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

variable "db_sslmode" {
  description = "psycopg sslmode — 'require' for prod/Lambda, 'disable' for Floci (no SSL)"
  type        = string
  default     = "require"
}

variable "sqs_endpoint_url" {
  description = "SQS endpoint URL — overridden in local to point at Floci"
  type        = string
}

variable "dynamodb_table_name" {
  description = "DynamoDB projections table name, from the storage module"
  type        = string
}

variable "dynamodb_table_arn" {
  description = "DynamoDB projections table ARN — used to scope IAM policies for ECS and Lambda"
  type        = string
  default     = ""
}

variable "dynamodb_kms_key_arn" {
  description = "ARN of the CMK encrypting DynamoDB. Empty when cmk_enabled is false."
  type        = string
  default     = ""
}

variable "sqs_kms_key_arn" {
  description = "ARN of the CMK encrypting SQS. Empty when cmk_enabled is false."
  type        = string
  default     = ""
}

variable "dynamodb_endpoint_url" {
  description = "DynamoDB endpoint URL — overridden in local to point at Floci"
  type        = string
  default     = ""
}

variable "vpc_id" {
  description = "VPC ID — needed for ALB target group"
  type        = string
  default     = ""
}

variable "public_subnet_ids" {
  description = "Public subnet IDs — the ALB lives here so the internet can reach it"
  type        = list(string)
  default     = []
}

variable "alb_sg_id" {
  description = "Security group ID for the ALB, from the networking module"
  type        = string
  default     = ""
}

variable "ecs_sg_id" {
  description = "Security group ID for ECS tasks, from the networking module"
  type        = string
  default     = ""
}

variable "redis_host" {
  description = "ElastiCache Redis host for the Spring Boot API"
  type        = string
  default     = ""
}

variable "redis_port" {
  description = "ElastiCache Redis port"
  type        = number
  default     = 6379
}

variable "cognito_jwk_set_uri" {
  description = "Cognito JWK set URI for Spring Boot JWT validation"
  type        = string
  default     = ""
}

variable "spring_profiles_active" {
  description = "Spring Boot active profile for the ECS task (prod in production, local-ecs in local)"
  type        = string
  default     = "prod"
}

variable "aws_access_key_id" {
  description = <<-EOT
    AWS access key ID injected into the ECS container environment.

    In production this must be left empty — the ECS task role is automatically
    surfaced to the container via the ECS container metadata endpoint
    (AWS_CONTAINER_CREDENTIALS_RELATIVE_URI), and the AWS SDK picks it up
    through ContainerCredentialsProvider without any explicit key.

    In local (Floci) this must be set to "test". Floci does not implement the
    ECS container metadata endpoint, so ContainerCredentialsProvider finds
    nothing and the SDK credential chain fails entirely. Injecting the dummy
    Floci credential ("test"/"test") directly as env vars is the only way to
    make the AWS SDK (DynamoDB, SQS, etc.) work inside a Floci ECS container.
  EOT
  type        = string
  default     = ""
}

variable "aws_secret_access_key" {
  description = <<-EOT
    AWS secret access key injected into the ECS container environment.
    See aws_access_key_id for the full explanation.
    Empty in prod (task role); set to "test" in local (Floci limitation).
  EOT
  type        = string
  default     = ""
  sensitive   = true
}
