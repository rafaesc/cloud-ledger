# ── OpenTelemetry / X-Ray ─────────────────────────────────────────────────────
# A single switch: tracing is on iff an X-Ray OTLP endpoint was supplied (prod). Empty -> local,
# where Floci can't reach X-Ray, so all OTel env vars and IAM are omitted.
locals {
  otel_enabled = var.otel_traces_endpoint != ""

  # Settings shared by the Spring task and both Lambdas. SigV4 signing to the X-Ray OTLP endpoint
  # is done by the ADOT agent (Java) / ADOT layer (Python) — no collector.
  otel_common_env = {
    # Wire format for the OTLP exporter. X-Ray's endpoint only accepts HTTP + protobuf (not gRPC).
    OTEL_EXPORTER_OTLP_PROTOCOL = "http/protobuf"
    # Where spans are POSTed: https://xray.<region>.amazonaws.com/v1/traces (the Transaction Search endpoint).
    OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = var.otel_traces_endpoint
    # Sampling strategy: honor the parent's sampled flag if present, else sample by trace-id ratio.
    # This keeps a trace all-sampled or all-dropped end to end across API -> SQS -> Lambdas.
    OTEL_TRACES_SAMPLER = "parentbased_traceidratio"
    # The ratio (0..1) used when this service makes the head sampling decision (no parent).
    OTEL_TRACES_SAMPLER_ARG = var.otel_traces_sampler_arg
    # Export traces only — no metrics pipeline (we don't send OTLP metrics to CloudWatch here).
    OTEL_METRICS_EXPORTER = "none"
    # Export traces only — no logs pipeline (app logs already go to CloudWatch Logs directly).
    OTEL_LOGS_EXPORTER = "none"
    # Disable CloudWatch Application Signals: it also ingests spans, which would double Transaction
    # Search cost. We want raw traces in X-Ray, not the Application Signals APM layer.
    OTEL_AWS_APPLICATION_SIGNALS_ENABLED = "false"
    # Pin the propagator set on BOTH the Java API and the Python Lambdas so trace context crosses
    # SQS consistently. Without this, the two ADOT distros can default to different propagators (e.g.
    # xray vs tracecontext), so the API injects one header and the projector extracts another and
    # misses it. tracecontext gives the W3C `traceparent` the code is built around (TraceContextSupport,
    # the outbox traceparent/tracestate columns, the projector carrier); xray keeps X-Ray-native interop.
    OTEL_PROPAGATORS = "tracecontext,baggage,xray"
  }

  # ECS (Spring) — task-definition environment shape: a list of { name, value }.
  otel_ecs_env = local.otel_enabled ? concat(
    [
      # Attach the ADOT auto-instrumentation agent baked into the image at /opt. JAVA_TOOL_OPTIONS
      # is read by the JVM at startup, so we never have to touch the Dockerfile CMD.
      { name = "JAVA_TOOL_OPTIONS", value = "-javaagent:/opt/aws-opentelemetry-agent.jar" },
      # service.name = how this app appears in X-Ray; deployment.environment = the "Hosted in" env.
      { name = "OTEL_RESOURCE_ATTRIBUTES", value = "service.name=cloudledger-${var.env}-api,deployment.environment=${var.env}" },
      # Inject trace context into SQS message attributes on SendMessage. The OTel AWS SDK
      # instrumentation defaults to the AWSTraceHeader *system* attribute (X-Ray format), which the
      # projector never reads — it only inspects messageAttributes. This flag switches injection to
      # the configured propagator writing regular message attributes (traceparent), which the
      # projector extracts. NOTE the exact spelling: ..._USE_PROPAGATOR_FOR_MESSAGING (propagatOR,
      # not propagatION) — the wrong spelling is silently ignored and the projector roots a new trace.
      { name = "OTEL_INSTRUMENTATION_AWS_SDK_EXPERIMENTAL_USE_PROPAGATOR_FOR_MESSAGING", value = "true" },
    ],
    [for k, v in local.otel_common_env : { name = k, value = v }]
  ) : []

  # Lambda — environment shape: a map.
  otel_lambda_env = local.otel_enabled ? merge(local.otel_common_env, {
    # The AWS base image runs this wrapper before the handler; /opt/otel-instrument (from the copied
    # ADOT layer) bootstraps auto-instrumentation. This is the layer-equivalent for container images.
    AWS_LAMBDA_EXEC_WRAPPER = "/opt/otel-instrument"
    # Tell the OTel Python bootstrap to use the AWS Distro (X-Ray-compatible IDs, AWS resource detectors).
    OTEL_PYTHON_DISTRO = "aws_distro"
    # ...and the AWS configurator, which wires the SigV4-signing OTLP exporter to the X-Ray endpoint.
    OTEL_PYTHON_CONFIGURATOR = "aws_configurator"
  }) : {}
}

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

# Lets the ADOT exporter sign (SigV4) and send spans to the X-Ray OTLP endpoint.
# Grants xray:PutTraceSegments / PutSpans / PutTelemetryRecords + sampling-target reads.
resource "aws_iam_role_policy_attachment" "lambda_xray" {
  count      = local.otel_enabled ? 1 : 0
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/AWSXrayWriteOnlyAccess"
}

resource "aws_iam_role_policy" "lambda_sqs" {
  name = "sqs-access"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [{
        Effect   = "Allow"
        Action   = ["sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
        Resource = var.queue_arn
      }],
      var.sqs_kms_key_arn != "" ? [{
        Effect   = "Allow"
        Action   = ["kms:GenerateDataKey", "kms:Decrypt"]
        Resource = var.sqs_kms_key_arn
      }] : []
    )
  })
}

resource "aws_iam_role_policy" "lambda_dynamodb" {
  name = "dynamodb-access"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [{
        Effect = "Allow"
        Action = ["dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:GetItem", "dynamodb:Query"]
        Resource = [
          var.dynamodb_table_arn != "" ? var.dynamodb_table_arn : "*",
          var.dynamodb_table_arn != "" ? "${var.dynamodb_table_arn}/index/*" : "*"
        ]
      }],
      var.dynamodb_kms_key_arn != "" ? [{
        Effect   = "Allow"
        Action   = ["kms:Decrypt", "kms:GenerateDataKey"]
        Resource = var.dynamodb_kms_key_arn
      }] : []
    )
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
  timeout       = 30

  environment {
    variables = merge({
      DB_HOST          = var.db_host
      DB_PORT          = tostring(var.db_port)
      DB_NAME          = var.db_name
      DB_USER          = var.db_user
      DB_PASSWORD      = var.db_password
      DB_SSLMODE       = var.db_sslmode
      SQS_QUEUE_URL    = var.queue_url
      SQS_ENDPOINT_URL = var.sqs_endpoint_url
      },
      local.otel_lambda_env,
      local.otel_enabled ? { OTEL_RESOURCE_ATTRIBUTES = "service.name=outbox-poller,deployment.environment=${var.env}" } : {}
    )
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
  timeout       = 30

  environment {
    variables = merge(
      { DYNAMODB_TABLE = var.dynamodb_table_name },
      var.dynamodb_endpoint_url != "" ? { DYNAMODB_ENDPOINT_URL = var.dynamodb_endpoint_url } : {},
      local.otel_lambda_env,
      local.otel_enabled ? { OTEL_RESOURCE_ATTRIBUTES = "service.name=projector,deployment.environment=${var.env}" } : {}
    )
  }

  logging_config {
    log_group  = aws_cloudwatch_log_group.projector.name
    log_format = "Text"
  }

  # No vpc_config — projector only needs SQS (trigger, managed by Lambda service) and DynamoDB.
  # Neither requires VPC access; removing VPC config gives the function direct internet egress.

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

  schedule_expression = "rate(30 minute)"

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
    Statement = concat(
      [{
        Effect   = "Allow"
        Action   = ["sqs:SendMessage", "sqs:GetQueueAttributes"]
        Resource = var.queue_arn
      }],
      var.sqs_kms_key_arn != "" ? [{
        Effect   = "Allow"
        Action   = ["kms:GenerateDataKey", "kms:Decrypt"]
        Resource = var.sqs_kms_key_arn
      }] : []
    )
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_xray" {
  count      = local.otel_enabled ? 1 : 0
  role       = aws_iam_role.ecs_task.name
  policy_arn = "arn:aws:iam::aws:policy/AWSXrayWriteOnlyAccess"
}

resource "aws_iam_role_policy" "ecs_task_dynamodb" {
  name = "dynamodb-read"
  role = aws_iam_role.ecs_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [{
        Effect = "Allow"
        Action = ["dynamodb:GetItem", "dynamodb:Query"]
        Resource = [
          var.dynamodb_table_arn,
          "${var.dynamodb_table_arn}/index/*"
        ]
      }],
      var.dynamodb_kms_key_arn != "" ? [{
        Effect   = "Allow"
        Action   = ["kms:Decrypt", "kms:GenerateDataKey"]
        Resource = var.dynamodb_kms_key_arn
      }] : []
    )
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

    environment = concat([
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
    ], local.otel_ecs_env)

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

  # The API boots in ~105-136s (Spring Boot + the ADOT Java agent transforming classes at startup).
  # The ALB marks a target unhealthy after ~90s (unhealthy_threshold 3 x interval 30s), so without a
  # grace period ECS kills tasks mid-boot for "failed ELB health checks" and deploys flap/stall.
  # This tells ECS to ignore ELB health failures for the first 240s, giving the app time to come up.
  health_check_grace_period_seconds = 240

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [var.ecs_sg_id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}
