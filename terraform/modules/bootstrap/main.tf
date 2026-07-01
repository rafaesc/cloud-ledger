provider "aws" {
  region = var.region
}

# ── S3 state bucket ─────────────────────────────────────────────────────────────

resource "aws_s3_bucket" "state" {
  bucket = var.bucket_name
  tags   = var.tags

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  # Expire old non-current versions to control storage cost; current versions kept forever.
  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = var.noncurrent_version_expiry_days
    }
  }
}

# ── GitHub Actions OIDC deploy role ─────────────────────────────────────────────
# Lets the prod CD workflow (.github/workflows/deploy-prod.yml) assume a role via
# GitHub's OIDC provider instead of storing long-lived AWS keys. Bootstrapped here
# (one-time, local state, admin creds) because envs/prod's own `terraform apply`
# runs AS this role — it cannot create the role it depends on (chicken-and-egg).

# One OIDC provider per account for GitHub. The thumbprint is no longer validated
# for this well-known IdP (AWS trusts the CA), but the argument is still required.
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
  tags            = var.tags
}

resource "aws_iam_role" "github_deploy" {
  name        = var.deploy_role_name
  description = "GitHub Actions OIDC deploy role for cloudledger prod"
  tags        = var.tags

  # Trust is scoped to exactly the prod-environment job of this repo — no other
  # branch, PR, or tag can assume it (the `sub` claim takes the environment form
  # only when the workflow job sets `environment: prod`).
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:environment:${var.github_environment}"
        }
      }
    }]
  })
}

# PowerUserAccess covers every service `terraform apply` touches except IAM.
resource "aws_iam_role_policy_attachment" "github_deploy_poweruser" {
  role       = aws_iam_role.github_deploy.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

# The IAM management PowerUserAccess omits — Terraform creates the ECS/Lambda
# roles + their policies, and must PassRole them to the services.
resource "aws_iam_role_policy" "github_deploy_iam" {
  name = "terraform-managed-iam"
  role = aws_iam_role.github_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "TerraformManagedIAM"
      Effect = "Allow"
      Action = [
        "iam:CreateRole", "iam:DeleteRole", "iam:GetRole", "iam:UpdateRole",
        "iam:TagRole", "iam:UntagRole", "iam:ListRoleTags",
        "iam:AttachRolePolicy", "iam:DetachRolePolicy", "iam:ListAttachedRolePolicies",
        "iam:PutRolePolicy", "iam:DeleteRolePolicy", "iam:GetRolePolicy", "iam:ListRolePolicies",
        "iam:CreatePolicy", "iam:DeletePolicy", "iam:GetPolicy", "iam:GetPolicyVersion",
        "iam:CreatePolicyVersion", "iam:DeletePolicyVersion", "iam:ListPolicyVersions",
        "iam:CreateServiceLinkedRole", "iam:PassRole",
        "iam:ListInstanceProfilesForRole"
      ]
      Resource = "*"
    }]
  })
}
