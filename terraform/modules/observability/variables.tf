variable "env" {
  description = "Environment name (e.g. prod). Used for resource naming and tags."
  type        = string
}

variable "indexing_percentage" {
  description = "Percentage of ingested spans X-Ray indexes for Transaction Search (0-100). Traces are already head-sampled at the SDK, so 100 keeps every sampled trace searchable."
  type        = number
  default     = 1
}
