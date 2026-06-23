output "bucket_name" {
  description = "Name of the S3 bucket storing Terraform state."
  value       = aws_s3_bucket.state.id
}

output "table_name" {
  description = "Name of the DynamoDB table used for state locking."
  value       = aws_dynamodb_table.locks.name
}

output "region" {
  description = "AWS region where the backend resources were created."
  value       = var.region
}

output "backend_config" {
  description = "Paste this backend block into each env's terraform.tf."
  value       = <<-EOT
    backend "s3" {
      bucket         = "${aws_s3_bucket.state.id}"
      region         = "${var.region}"
      dynamodb_table = "${aws_dynamodb_table.locks.name}"
      encrypt        = true
      # key is env-specific, e.g.:
      #   cloudledger/dev/terraform.tfstate
      #   cloudledger/prod/terraform.tfstate
    }
  EOT
}
