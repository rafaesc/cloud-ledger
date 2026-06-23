terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.100.0"
    }
  }

  required_version = ">= 1.2"

  # Intentionally no backend block — this module bootstraps the backend.
  # State is stored locally until it is migrated or discarded after creation.
}
