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

output "sqs_kms_key_arn" {
  value = var.cmk_enabled ? aws_kms_key.sqs[0].arn : ""
}
