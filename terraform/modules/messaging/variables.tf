variable "env" {
  description = "Environment name (local, prod)"
  type        = string
}

variable "cmk_enabled" {
  description = "Create and use a customer-managed KMS key for SQS. Disable for local environments."
  type        = bool
  default     = true
}