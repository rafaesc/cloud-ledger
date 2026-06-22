# ── Aurora PostgreSQL ─────────────────────────────────────────────────────────

resource "aws_db_subnet_group" "main" {
  name       = "cloudledger-${var.env}"
  subnet_ids = var.subnet_ids

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_rds_cluster" "main" {
  cluster_identifier     = "cloudledger-${var.env}"
  engine                 = "aurora-postgresql"
  engine_version         = "16.6"
  database_name          = "cloudledger"
  master_username        = var.rds_username
  master_password        = var.rds_password
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.rds_sg_id]
  skip_final_snapshot    = true

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_rds_cluster_instance" "writer" {
  identifier         = "cloudledger-${var.env}-writer"
  cluster_identifier = aws_rds_cluster.main.id
  instance_class     = "db.t3.medium"
  # hardcoded to avoid Floci read-back returning "postgres" instead of "aurora-postgresql"
  engine         = "aurora-postgresql"
  engine_version = "16.6"

  tags = { Name = "cloudledger-${var.env}-writer", Project = "cloud-ledger" }
}

# ── ElastiCache ──────────────────────────────────────────────────────────────

resource "aws_elasticache_subnet_group" "main" {
  count      = var.elasticache_subnet_group_enabled ? 1 : 0
  name       = "cloudledger-${var.env}"
  subnet_ids = var.subnet_ids
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "cloudledger-${var.env}"
  description          = "CloudLedger Redis cache"
  node_type            = "cache.t3.micro"
  num_cache_clusters   = 1
  port                 = 6379
  subnet_group_name    = var.elasticache_subnet_group_enabled ? aws_elasticache_subnet_group.main[0].name : null
  security_group_ids   = [var.elasticache_sg_id]

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# ── DynamoDB ─────────────────────────────────────────────────────────────────

resource "aws_dynamodb_table" "projections" {
  name         = "cloudledger-projections"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  attribute {
    name = "PK"
    type = "S"
  }

  attribute {
    name = "SK"
    type = "S"
  }

  attribute {
    name = "GSI1PK"
    type = "S"
  }

  attribute {
    name = "GSI1SK"
    type = "S"
  }

  global_secondary_index {
    name               = "GSI1"
    hash_key           = "GSI1PK"
    range_key          = "GSI1SK"
    projection_type    = "INCLUDE"
    non_key_attributes = ["status", "currency", "opened_at"]
  }

  server_side_encryption { enabled = true }
  point_in_time_recovery { enabled = true }

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}
