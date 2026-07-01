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

variable "api_image_tag" {
  description = "Immutable API image tag to deploy (e.g. v1.2.3). Supplied by CD via TF_VAR_api_image_tag."
  type        = string
  default     = "latest"
}

variable "git_commit" {
  description = "Git commit SHA of the deployed build. Supplied by CD via TF_VAR_git_commit."
  type        = string
  default     = ""
}
