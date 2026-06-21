output "db_address" {
  value = aws_db_instance.main.address
}

output "db_name" {
  value = aws_db_instance.main.db_name
}

output "redis_host" {
  value = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.projections.name
}
