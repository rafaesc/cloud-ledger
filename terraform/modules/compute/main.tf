# ── ECR ──────────────────────────────────────────────────────────────────────

resource "aws_ecr_repository" "main" {
  name = "cloudledger/outbox-poller"

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# ── IAM — Lambda ─────────────────────────────────────────────────────────────

resource "aws_iam_role" "lambda" {
  name = "cloudledger-${var.env}-lambda"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "lambda_vpc" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_iam_role_policy" "lambda_sqs" {
  name = "sqs-access"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
      Resource = var.queue_arn
    }]
  })
}

# ── Lambda ───────────────────────────────────────────────────────────────────

resource "aws_lambda_function" "main" {
  function_name = "outbox-poller"
  role          = aws_iam_role.lambda.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.main.repository_url}:latest"

  environment {
    variables = {
      DB_HOST          = var.db_host
      DB_PORT          = tostring(var.db_port)
      DB_NAME          = var.db_name
      DB_USER          = var.db_user
      DB_PASSWORD      = var.db_password
      SQS_QUEUE_URL    = var.queue_url
      SQS_ENDPOINT_URL = var.sqs_endpoint_url
    }
  }

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = [var.lambda_sg_id]
  }

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# ── IAM — Scheduler ──────────────────────────────────────────────────────────

resource "aws_iam_role" "scheduler" {
  name = "cloudledger-${var.env}-scheduler"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
    }]
  })

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_iam_role_policy" "scheduler_invoke_lambda" {
  name = "invoke-lambda"
  role = aws_iam_role.scheduler.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = aws_lambda_function.main.arn
    }]
  })
}

# ── EventBridge Scheduler ────────────────────────────────────────────────────

resource "aws_scheduler_schedule_group" "main" {
  name = "cloudledger-${var.env}"

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_scheduler_schedule" "main" {
  name       = "outbox-poller"
  group_name = aws_scheduler_schedule_group.main.name

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "rate(1 minute)"

  target {
    arn      = aws_lambda_function.main.arn
    role_arn = aws_iam_role.scheduler.arn
  }
}
