data "aws_availability_zones" "available" {}

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


resource "aws_security_group" "rds" {
  name   = "cloudledger-${var.env}-rds"
  vpc_id = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id]
  }

  tags = { Name = "cloudledger-${var.env}-rds", Project = "cloud-ledger" }
}

resource "aws_security_group" "elasticache" {
  name   = "cloudledger-${var.env}-elasticache"
  vpc_id = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id]
  }

  tags = { Name = "cloudledger-${var.env}-elasticache", Project = "cloud-ledger" }
}
