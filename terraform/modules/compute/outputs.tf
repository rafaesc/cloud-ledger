output "lambda_arn" {
  value = aws_lambda_function.main.arn
}

output "ecr_repository_url" {
  value = aws_ecr_repository.main.repository_url
}

output "projector_lambda_arn" {
  value = aws_lambda_function.projector.arn
}

output "projector_ecr_repository_url" {
  value = aws_ecr_repository.projector.repository_url
}

output "api_ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "alb_dns_name" {
  value = aws_lb.main.dns_name
}
