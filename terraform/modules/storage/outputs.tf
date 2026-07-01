output "db_address" {
  value = aws_rds_cluster.main.endpoint
}

output "db_name" {
  value = aws_rds_cluster.main.database_name
}

output "redis_host" {
  value = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "redis_node_id" {
  description = "ElastiCache member cluster (node) id — CacheClusterId dimension for AWS/ElastiCache hit-rate metrics."
  # Floci does not populate member_clusters (empty set); fall back to "" so the local
  # env — which never consumes this output — can still evaluate it. Prod returns the real node id.
  value = try(tolist(aws_elasticache_replication_group.main.member_clusters)[0], "")
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.projections.name
}

output "dynamodb_table_arn" {
  value = aws_dynamodb_table.projections.arn
}

output "dynamodb_kms_key_arn" {
  value = var.cmk_enabled ? aws_kms_key.dynamodb[0].arn : ""
}
