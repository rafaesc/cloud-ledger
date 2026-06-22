output "db_address" {
  value = aws_rds_cluster.main.endpoint
}

output "db_name" {
  value = aws_rds_cluster.main.database_name
}

output "redis_host" {
  value = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.projections.name
}
