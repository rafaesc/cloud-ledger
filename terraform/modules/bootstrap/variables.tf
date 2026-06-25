variable "region" {
  description = "AWS region for the state bucket and lock table."
  type        = string
  default     = "us-east-1"
}

variable "bucket_name" {
  description = "Name of the S3 bucket that stores Terraform state."
  type        = string
  default     = "cloudledger-terraform-state"
}

variable "noncurrent_version_expiry_days" {
  description = "Days after which non-current object versions are deleted."
  type        = number
  default     = 90
}

variable "tags" {
  description = "Tags applied to all resources."
  type        = map(string)
  default = {
    project   = "cloudledger"
    managed   = "terraform"
    component = "bootstrap"
  }
}
