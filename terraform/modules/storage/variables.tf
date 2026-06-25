variable "env" {
  description = "Environment name (local, prod)"
  type        = string
}

variable "subnet_ids" {
  description = "Private subnet IDs from the networking module"
  type        = list(string)
}

variable "rds_sg_id" {
  description = "Security group ID for RDS, from the networking module"
  type        = string
}

variable "elasticache_sg_id" {
  description = "Security group ID for ElastiCache, from the networking module"
  type        = string
}

variable "elasticache_subnet_group_enabled" {
  description = "Set to false for environments where ElastiCache subnet groups aren't supported (e.g. Floci)"
  type        = bool
  default     = true
}

variable "elasticache_tls_enabled" {
  description = "Enable TLS (transit encryption) on the ElastiCache replication group. Set to false for Floci."
  type        = bool
  default     = true
}

variable "rds_username" {
  type = string
}

variable "rds_password" {
  type      = string
  sensitive = true
}

variable "cmk_enabled" {
  description = "Create and use customer-managed KMS keys for Aurora and DynamoDB. Disable for local environments."
  type        = bool
  default     = true
}
