output "user_pool_id" {
  description = "Cognito User Pool ID"
  value       = aws_cognito_user_pool.main.id
}

output "client_id" {
  description = "M2M app client ID"
  value       = aws_cognito_user_pool_client.api.id
}

output "client_secret" {
  description = "M2M app client secret"
  value       = aws_cognito_user_pool_client.api.client_secret
  sensitive   = true
}

output "admin_client_id" {
  description = "M2M admin app client ID (holds the api/admin scope)"
  value       = aws_cognito_user_pool_client.admin.id
}

output "admin_client_secret" {
  description = "M2M admin app client secret"
  value       = aws_cognito_user_pool_client.admin.client_secret
  sensitive   = true
}
