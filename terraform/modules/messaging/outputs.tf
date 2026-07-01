output "queue_url" {
  value = aws_sqs_queue.main.url
}

output "queue_arn" {
  value = aws_sqs_queue.main.arn
}

output "dlq_url" {
  value = aws_sqs_queue.event_dlq.url
}

output "dlq_arn" {
  value = aws_sqs_queue.event_dlq.arn
}

output "queue_name" {
  description = "Main SQS queue name — QueueName dimension for AWS/SQS metrics (projection lag)."
  value       = aws_sqs_queue.main.name
}

output "dlq_name" {
  description = "DLQ name — QueueName dimension for the DLQ-depth widget."
  value       = aws_sqs_queue.event_dlq.name
}

output "sqs_kms_key_arn" {
  value = var.cmk_enabled ? aws_kms_key.sqs[0].arn : ""
}
