provider "aws" {
  region = var.region
  # Credentials come from the environment (IAM role, AWS_PROFILE, or env vars).
  # No access_key/secret_key hardcoded here.
}

module "networking" {
  source = "../../modules/networking"

  env                      = "prod"
  create_sqs_endpoint      = true
  create_dynamodb_endpoint = true
  create_xray_endpoint     = true
}

module "messaging" {
  source = "../../modules/messaging"

  env         = "prod"
  cmk_enabled = true
}

module "storage" {
  source = "../../modules/storage"

  env                              = "prod"
  cmk_enabled                      = true
  subnet_ids                       = module.networking.private_subnet_ids
  rds_sg_id                        = module.networking.rds_sg_id
  elasticache_sg_id                = module.networking.elasticache_sg_id
  rds_username                     = var.rds_username
  rds_password                     = var.rds_password
  elasticache_subnet_group_enabled = true
  elasticache_tls_enabled          = true
}

module "compute" {
  source = "../../modules/compute"

  env                  = "prod"
  subnet_ids           = module.networking.private_subnet_ids
  lambda_sg_id         = module.networking.lambda_sg_id
  queue_url            = module.messaging.queue_url
  queue_arn            = module.messaging.queue_arn
  sqs_kms_key_arn      = module.messaging.sqs_kms_key_arn
  db_host              = module.storage.db_address
  db_name              = module.storage.db_name
  db_user              = var.rds_username
  db_password          = var.rds_password
  sqs_endpoint_url     = ""
  dynamodb_table_name  = module.storage.dynamodb_table_name
  dynamodb_table_arn   = module.storage.dynamodb_table_arn
  dynamodb_kms_key_arn = module.storage.dynamodb_kms_key_arn

  # ECS + ALB
  vpc_id                 = module.networking.vpc_id
  public_subnet_ids      = module.networking.public_subnet_ids
  alb_sg_id              = module.networking.alb_sg_id
  ecs_sg_id              = module.networking.ecs_sg_id
  redis_host             = module.storage.redis_host
  redis_port             = 6379
  cognito_jwk_set_uri    = "https://cognito-idp.${var.region}.amazonaws.com/${module.auth.user_pool_id}/.well-known/jwks.json"
  spring_profiles_active = "prod"

  # Enables collector-less OTel -> X-Ray (SigV4) tracing across the API task and both Lambdas.
  # Must be paired with Transaction Search being enabled on the account (see xray.tf).
  otel_traces_endpoint = "https://xray.${var.region}.amazonaws.com/v1/traces"
  # Low-traffic ledger: head-sample every request so manual testing is actually traceable.
  # Dial this back (e.g. 0.05) once real traffic makes 100% sampling costly.
  otel_traces_sampler_arg = "1.0"
  # aws_access_key_id / aws_secret_access_key intentionally omitted —
  # the ECS task role surfaces credentials automatically via the container metadata endpoint.
}

module "auth" {
  source = "../../modules/auth"

  env = "prod"
}

module "observability" {
  source = "../../modules/observability"

  env = "prod"
  # Index every sampled span into the searchable trace store. Without this the default (1%)
  # means the X-Ray traces console shows almost nothing even though spans reach aws/spans.
  indexing_percentage = 100
}
