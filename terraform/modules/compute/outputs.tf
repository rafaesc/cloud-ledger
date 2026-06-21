output "lambda_arn" {
  value = aws_lambda_function.main.arn
}

output "ecr_repository_url" {
  value = aws_ecr_repository.main.repository_url
}
