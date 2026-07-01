output "transaction_search_stack_id" {
  description = "ID of the CloudFormation stack that enables X-Ray Transaction Search."
  value       = aws_cloudformation_stack.xray_transaction_search.id
}

output "dashboard_name" {
  description = "Name of the CloudWatch operations dashboard."
  value       = aws_cloudwatch_dashboard.main.dashboard_name
}

output "dashboard_url" {
  description = "Console URL for the CloudWatch operations dashboard."
  value       = "https://${var.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards/dashboard/${aws_cloudwatch_dashboard.main.dashboard_name}"
}
