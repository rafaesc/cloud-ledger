variable "env" {
  description = "Environment name (local, prod)"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.101.0/24", "10.0.102.0/24"]
}

variable "create_sqs_endpoint" {
  description = "Create an SQS Interface VPC Endpoint so Lambda in private subnets can reach SQS without internet egress"
  type        = bool
  default     = false
}

variable "create_dynamodb_endpoint" {
  description = "Create a DynamoDB Gateway VPC Endpoint (free) to route DynamoDB traffic through the AWS backbone instead of the internet"
  type        = bool
  default     = false
}