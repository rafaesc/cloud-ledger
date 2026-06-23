# ── ECR ──────────────────────────────────────────────────────────────────────

resource "aws_ecr_repository" "api" {
  name         = "cloudledger/api"
  force_delete = true

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_ecr_repository" "main" {
  name         = "cloudledger/outbox-poller"
  force_delete = true

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_ecr_repository" "projector" {
  name         = "cloudledger/projector"
  force_delete = true

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

resource "aws_iam_role_policy" "lambda_dynamodb" {
  name = "dynamodb-access"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:GetItem", "dynamodb:Query"]
      Resource = "*"
    }]
  })
}

# ── CloudWatch Logs — Lambda ──────────────────────────────────────────────────
# Pre-creating the log groups lets us control retention (default is infinite).
# Lambda would create them automatically, but with no retention set.

resource "aws_cloudwatch_log_group" "outbox_poller" {
  name              = "/aws/lambda/outbox-poller"
  retention_in_days = 7

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_cloudwatch_log_group" "projector" {
  name              = "/aws/lambda/projector"
  retention_in_days = 7

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
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

  logging_config {
    log_group  = aws_cloudwatch_log_group.outbox_poller.name
    log_format = "Text"
  }

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = [var.lambda_sg_id]
  }

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# ── Lambda — projector ───────────────────────────────────────────────────────

resource "aws_lambda_function" "projector" {
  function_name = "projector"
  role          = aws_iam_role.lambda.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.projector.repository_url}:latest"

  environment {
    variables = merge(
      { DYNAMODB_TABLE = var.dynamodb_table_name },
      var.dynamodb_endpoint_url != "" ? { DYNAMODB_ENDPOINT_URL = var.dynamodb_endpoint_url } : {}
    )
  }

  logging_config {
    log_group  = aws_cloudwatch_log_group.projector.name
    log_format = "Text"
  }

  vpc_config {
    subnet_ids         = var.subnet_ids
    security_group_ids = [var.lambda_sg_id]
  }

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_lambda_event_source_mapping" "sqs_to_projector" {
  event_source_arn = var.queue_arn
  function_name    = aws_lambda_function.projector.arn
  batch_size       = 10
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

# ── ALB ──────────────────────────────────────────────────────────────────────
# The front door: receives HTTP traffic from the internet and forwards it to ECS.

resource "aws_lb" "main" {
  name               = "cloudledger-${var.env}"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.alb_sg_id]
  subnets            = var.public_subnet_ids

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# Target group: the ALB sends requests here; ECS tasks register themselves as targets.
resource "aws_lb_target_group" "api" {
  name        = "cloudledger-${var.env}-api"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
  }

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# Listener: ALB listens on port 80 and forwards all traffic to the target group.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

# ── ECS ──────────────────────────────────────────────────────────────────────

# The cluster is just a logical namespace — Fargate manages the actual machines.
resource "aws_ecs_cluster" "main" {
  name = "cloudledger-${var.env}"

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# ── IAM — ECS ────────────────────────────────────────────────────────────────

# Execution role: used by ECS itself to pull the Docker image from ECR and send logs to CloudWatch.
resource "aws_iam_role" "ecs_task_execution" {
  name = "cloudledger-${var.env}-ecs-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Task role: used by the running Spring Boot container — what AWS services it can call.
resource "aws_iam_role" "ecs_task" {
  name = "cloudledger-${var.env}-ecs-task"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_iam_role_policy" "ecs_task_sqs" {
  name = "sqs-access"
  role = aws_iam_role.ecs_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sqs:SendMessage", "sqs:GetQueueAttributes"]
      Resource = var.queue_arn
    }]
  })
}

# ── CloudWatch Logs ───────────────────────────────────────────────────────────
# Where the Spring Boot container writes its stdout/stderr.

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/cloudledger-${var.env}-api"
  retention_in_days = 7

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# ── ECS Task Definition ───────────────────────────────────────────────────────
# Describes HOW to run one container: image, CPU/memory, port, env vars, logs.

resource "aws_ecs_task_definition" "api" {
  family                   = "cloudledger-${var.env}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name  = "api"
    image = "${aws_ecr_repository.api.repository_url}:latest"

    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.spring_profiles_active },
      { name = "DB_CLUSTER_ENDPOINT", value = var.db_host },
      { name = "DB_NAME", value = var.db_name },
      { name = "DB_USERNAME", value = var.db_user },
      { name = "DB_PASSWORD", value = var.db_password },
      { name = "SQS_QUEUE_URL", value = var.queue_url },
      { name = "SQS_ENDPOINT_URL", value = var.sqs_endpoint_url },
      { name = "REDIS_HOST", value = var.redis_host },
      { name = "REDIS_PORT", value = tostring(var.redis_port) },
      { name = "DYNAMODB_ENDPOINT_URL", value = var.dynamodb_endpoint_url },
      { name = "DYNAMODB_TABLE_NAME", value = var.dynamodb_table_name },
      { name = "COGNITO_JWK_SET_URI", value = var.cognito_jwk_set_uri },
      { name = "AWS_ACCESS_KEY_ID", value = var.aws_access_key_id },
      { name = "AWS_SECRET_ACCESS_KEY", value = var.aws_secret_access_key },
      { name = "AWS_DEFAULT_REGION", value = "us-east-1" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = "us-east-1"
        "awslogs-stream-prefix" = "api"
      }
    }
  }])

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

# ── ECS Service ───────────────────────────────────────────────────────────────
# Keeps N copies of the task running. If one crashes, ECS restarts it.
# Also registers/deregisters tasks with the ALB automatically.

resource "aws_ecs_service" "api" {
  name            = "cloudledger-${var.env}-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = var.subnet_ids
    security_groups = [var.ecs_sg_id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}
