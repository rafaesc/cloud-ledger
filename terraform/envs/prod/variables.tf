variable "region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

variable "rds_username" {
  description = "Master username for the Aurora cluster."
  type        = string
  default     = "cloudledger"
}

variable "rds_password" {
  description = "Master password for the Aurora cluster. Supply via TF_VAR_rds_password or -var."
  type        = string
  sensitive   = true
}
