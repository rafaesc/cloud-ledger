resource "aws_sqs_queue" "main" {
  name = "cloudledger-events"

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}
