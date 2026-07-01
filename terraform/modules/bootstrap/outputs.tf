output "bucket_name" {
  description = "Name of the S3 bucket storing Terraform state."
  value       = aws_s3_bucket.state.id
}

output "region" {
  description = "AWS region where the backend resources were created."
  value       = var.region
}

output "github_deploy_role_arn" {
  description = "ARN of the GitHub Actions OIDC deploy role — set as the AWS_DEPLOY_ROLE_ARN variable on the prod GitHub Environment."
  value       = aws_iam_role.github_deploy.arn
}

output "backend_config" {
  description = "Paste this backend block into each env's terraform.tf."
  value       = <<-EOT
    backend "s3" {
      bucket       = "${aws_s3_bucket.state.id}"
      region       = "${var.region}"
      use_lockfile = true
      encrypt      = true
      # key is env-specific, e.g.:
      #   cloudledger/dev/terraform.tfstate
      #   cloudledger/prod/terraform.tfstate
    }
  EOT
}
