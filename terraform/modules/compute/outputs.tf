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

output "alb_arn_suffix" {
  description = "ALB ARN suffix, used as the LoadBalancer dimension for AWS/ApplicationELB CloudWatch metrics."
  value       = aws_lb.main.arn_suffix
}

output "target_group_arn_suffix" {
  description = "Target group ARN suffix, used as the TargetGroup dimension for latency metrics."
  value       = aws_lb_target_group.api.arn_suffix
}

output "api_log_group_name" {
  description = "ECS API CloudWatch log group name — the metric filter that powers the transfer-rate widget attaches here."
  value       = aws_cloudwatch_log_group.api.name
}
