resource "aws_kms_key" "sqs" {
  description             = "cloudledger/${var.env} SQS CMK"
  deletion_window_in_days = 7
  enable_key_rotation     = false

  tags = { Name = "cloudledger-sqs-${var.env}", Project = "cloud-ledger" }
}

resource "aws_kms_alias" "sqs" {
  name          = "alias/cloudledger-sqs-${var.env}"
  target_key_id = aws_kms_key.sqs.key_id
}

resource "aws_sqs_queue" "event_dlq" {
  name                      = "cloudledger-events-dlq-${var.env}"
  kms_master_key_id         = aws_kms_key.sqs.id
  message_retention_seconds = 1209600 # 14 days

  tags = { Name = "cloudledger-events-dlq-${var.env}", Project = "cloud-ledger" }
}

resource "aws_sqs_queue" "main" {
  name              = "cloudledger-events-${var.env}"
  kms_master_key_id = aws_kms_key.sqs.id

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.event_dlq.arn
    maxReceiveCount     = 5
  })

  tags = { Name = "cloudledger-events-${var.env}", Project = "cloud-ledger" }
}
