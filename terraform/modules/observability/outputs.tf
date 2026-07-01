output "transaction_search_stack_id" {
  description = "ID of the CloudFormation stack that enables X-Ray Transaction Search."
  value       = aws_cloudformation_stack.xray_transaction_search.id
}
