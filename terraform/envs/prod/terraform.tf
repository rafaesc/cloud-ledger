terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.51.0"
    }
  }

  required_version = ">= 1.2"

  backend "s3" {
    bucket       = "cloudledger-terraform-state"
    key          = "cloudledger/prod/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
    encrypt      = true
  }
}
