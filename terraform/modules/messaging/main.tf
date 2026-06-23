resource "aws_kms_key" "sqs" {
  count                   = var.cmk_enabled ? 1 : 0
  description             = "cloudledger/${var.env} SQS CMK"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = { Name = "cloudledger-sqs-${var.env}", Project = "cloud-ledger" }
}

resource "aws_kms_alias" "sqs" {
  count         = var.cmk_enabled ? 1 : 0
  name          = "alias/cloudledger-sqs-${var.env}"
  target_key_id = aws_kms_key.sqs[0].key_id
}

resource "aws_sqs_queue" "event_dlq" {
  name                      = "cloudledger-events-dlq-${var.env}"
  kms_master_key_id         = var.cmk_enabled ? aws_kms_key.sqs[0].id : null
  message_retention_seconds = 1209600 # 14 days

  tags = { Name = "cloudledger-events-dlq-${var.env}", Project = "cloud-ledger" }
}

resource "aws_sqs_queue" "main" {
  name              = "cloudledger-events-${var.env}"
  kms_master_key_id = var.cmk_enabled ? aws_kms_key.sqs[0].id : null

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.event_dlq.arn
    maxReceiveCount     = 5
  })

  tags = { Name = "cloudledger-events-${var.env}", Project = "cloud-ledger" }
}
