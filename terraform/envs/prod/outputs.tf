output "cognito_pool_id" {
  description = "Cognito User Pool ID — used to build the JWK set URI"
  value       = module.auth.user_pool_id
}

output "cognito_client_id" {
  description = "M2M app client ID — used in the client_credentials token request"
  value       = module.auth.client_id
}

output "cognito_client_secret" {
  description = "M2M app client secret"
  value       = module.auth.client_secret
  sensitive   = true
}

output "alb_dns_name" {
  description = "ALB DNS name — entry point for all API traffic"
  value       = module.compute.alb_dns_name
}
