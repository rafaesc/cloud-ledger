data "aws_availability_zones" "available" {}
data "aws_region" "current" {}

# The VPC's implicit main route table — used by private subnets that have no explicit association.
data "aws_route_table" "main" {
  vpc_id = aws_vpc.main.id
  filter {
    name   = "association.main"
    values = ["true"]
  }
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_subnet" "private" {
  count             = length(var.private_subnet_cidrs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index]


  tags = { Name = "cloudledger-${var.env}-private-${count.index + 1}", Project = "cloud-ledger" }
}

resource "aws_subnet" "public" {
  count             = length(var.public_subnet_cidrs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.public_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = { Name = "cloudledger-${var.env}-public-${count.index + 1}", Project = "cloud-ledger" }
}

# Route table: public subnets → IGW (required for ALB and ECS Fargate with assign_public_ip)
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "cloudledger-${var.env}-public", Project = "cloud-ledger" }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Lambda security group — accesss to internet
resource "aws_security_group" "lambda" {
  name   = "cloudledger-${var.env}-lambda"
  vpc_id = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "cloudledger-${var.env}-lambda", Project = "cloud-ledger" }
}

# ALB security group — accepts HTTP/HTTPS from the internet
resource "aws_security_group" "alb" {
  name   = "cloudledger-${var.env}-alb"
  vpc_id = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "cloudledger-${var.env}-alb", Project = "cloud-ledger" }
}

# ECS security group — only accepts traffic from the ALB, on the Spring Boot port
resource "aws_security_group" "ecs" {
  name   = "cloudledger-${var.env}-ecs"
  vpc_id = aws_vpc.main.id

  ingress {
    description     = "API port from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "cloudledger-${var.env}-ecs", Project = "cloud-ledger" }
}

# RDS security group — accesss from Lambda & ECS
resource "aws_security_group" "rds" {
  name   = "cloudledger-${var.env}-rds"
  vpc_id = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id, aws_security_group.ecs.id]
  }

  tags = { Name = "cloudledger-${var.env}-rds", Project = "cloud-ledger" }
}

# SQS Interface VPC Endpoint — lets the outbox-poller Lambda (private subnet, no internet)
# reach SQS without a NAT gateway. Only provisioned in prod; not needed locally (Floci).
resource "aws_security_group" "vpc_endpoint" {
  count  = var.create_sqs_endpoint ? 1 : 0
  name   = "cloudledger-${var.env}-vpc-endpoint"
  vpc_id = aws_vpc.main.id

  ingress {
    description     = "HTTPS from Lambda"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id]
  }

  tags = { Name = "cloudledger-${var.env}-vpc-endpoint", Project = "cloud-ledger" }
}

resource "aws_vpc_endpoint" "sqs" {
  count               = var.create_sqs_endpoint ? 1 : 0
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${data.aws_region.current.region}.sqs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  security_group_ids  = [aws_security_group.vpc_endpoint[0].id]
  private_dns_enabled = true

  tags = { Name = "cloudledger-${var.env}-sqs", Project = "cloud-ledger" }
}

# DynamoDB Gateway VPC Endpoint — free; routes DynamoDB traffic from ECS (public subnets)
# and Lambda (private subnets) through the AWS backbone instead of the internet.
# Gateway endpoints work via route table entries, not ENIs, so no security group is needed.
resource "aws_vpc_endpoint" "dynamodb" {
  count             = var.create_dynamodb_endpoint ? 1 : 0
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${data.aws_region.current.region}.dynamodb"
  vpc_endpoint_type = "Gateway"
  route_table_ids = [
    aws_route_table.public.id,    # ECS Fargate (public subnets)
    data.aws_route_table.main.id, # Lambda (private subnets use the VPC main route table)
  ]

  tags = { Name = "cloudledger-${var.env}-dynamodb", Project = "cloud-ledger" }
}

resource "aws_security_group" "elasticache" {
  name   = "cloudledger-${var.env}-elasticache"
  vpc_id = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id, aws_security_group.ecs.id]
  }

  tags = { Name = "cloudledger-${var.env}-elasticache", Project = "cloud-ledger" }
}
