provider "aws" {
  access_key = "test"
  secret_key = "test"
  region     = "us-east-1"

  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    dynamodb        = "http://localhost:4566"
    ec2             = "http://localhost:4566"
    ecr             = "http://localhost:4566"
    ecs             = "http://localhost:4566"
    elasticache     = "http://localhost:4566"
    elasticloadbalancingv2 = "http://localhost:4566"
    iam             = "http://localhost:4566"
    kms             = "http://localhost:4566"
    lambda          = "http://localhost:4566"
    logs            = "http://localhost:4566"
    rds             = "http://localhost:4566"
    scheduler       = "http://localhost:4566"
    sqs             = "http://localhost:4566"
    cognitoidp      = "http://localhost:4566"
  }
}

module "networking" {
  source = "../../modules/networking"

  env = "local"
}

module "messaging" {
  source = "../../modules/messaging"

  env = "local"
}

module "storage" {
  source = "../../modules/storage"

  env                              = "local"
  subnet_ids                       = module.networking.private_subnet_ids
  rds_sg_id                        = module.networking.rds_sg_id
  elasticache_sg_id                = module.networking.elasticache_sg_id
  rds_username                     = "admin"
  rds_password                     = "secret123"
  elasticache_subnet_group_enabled = false
}

module "compute" {
  source = "../../modules/compute"

  env                   = "local"
  subnet_ids            = module.networking.private_subnet_ids
  lambda_sg_id          = module.networking.lambda_sg_id
  queue_url             = module.messaging.queue_url
  queue_arn             = module.messaging.queue_arn
  db_host               = "floci-rds-cloudledger-local"
  db_name               = module.storage.db_name
  db_user               = "admin"
  db_password           = "secret123"
  sqs_endpoint_url      = "http://floci:4566"
  dynamodb_table_name   = module.storage.dynamodb_table_name
  dynamodb_endpoint_url = "http://floci:4566"

  # ECS + ALB
  vpc_id                 = module.networking.vpc_id
  public_subnet_ids      = module.networking.public_subnet_ids
  alb_sg_id              = module.networking.alb_sg_id
  ecs_sg_id              = module.networking.ecs_sg_id
  redis_host             = "floci-valkey-cloudledger-local" # Docker container name, not the host-mapped port
  redis_port             = 6379                             # internal container port, not 7379
  cognito_jwk_set_uri    = "http://floci:4566/${module.auth.user_pool_id}/.well-known/jwks.json"
  spring_profiles_active = "local-ecs"
  aws_access_key_id      = "test"
  aws_secret_access_key  = "test"
}

module "auth" {
  source = "../../modules/auth"

  env = "local"
}