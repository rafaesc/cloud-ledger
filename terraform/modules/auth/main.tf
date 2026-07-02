resource "aws_cognito_user_pool" "main" {
  name = "cloudledger-${var.env}"

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}

resource "aws_cognito_resource_server" "api" {
  identifier   = "https://api.getcloudledger.com"
  name         = "CloudLedger API"
  user_pool_id = aws_cognito_user_pool.main.id

  scope {
    scope_name        = "write"
    scope_description = "Create accounts, deposits, withdrawals, transfers"
  }

  scope {
    scope_name        = "read"
    scope_description = "Read account state (future)"
  }

  scope {
    scope_name        = "admin"
    scope_description = "Operational admin actions (e.g. rebuild DynamoDB projections)"
  }
}

resource "aws_cognito_user_pool_client" "api" {
  name                                 = "m2m-client"
  user_pool_id                         = aws_cognito_user_pool.main.id
  generate_secret                      = true
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_scopes                 = ["https://api.getcloudledger.com/read", "https://api.getcloudledger.com/write"]
  allowed_oauth_flows_user_pool_client = true

  depends_on = [aws_cognito_resource_server.api]
}

# Separate M2M client for operational/admin tooling. Kept distinct from the app client so the
# powerful `admin` scope is never handed to the regular read/write workload.
resource "aws_cognito_user_pool_client" "admin" {
  name                                 = "m2m-admin-client"
  user_pool_id                         = aws_cognito_user_pool.main.id
  generate_secret                      = true
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_scopes                 = ["https://api.getcloudledger.com/admin"]
  allowed_oauth_flows_user_pool_client = true

  depends_on = [aws_cognito_resource_server.api]
}

## It doesn't require a Cognito domain for Floci
## Relaxed OAuth token endpoint for grant_type=client_credentials: POST http://localhost:4566/cognito-idp/oauth2/token
## Tokens issued by Floci can be validated using the discovery: http://localhost:4566/$POOL_ID/.well-known/openid-configuration
## Cognito domain — required in prod for the OAuth token endpoint (client_credentials grant).
resource "aws_cognito_user_pool_domain" "main" {
  count        = var.env == "prod" ? 1 : 0
  domain       = "cloudledger-${var.env}"
  user_pool_id = aws_cognito_user_pool.main.id
}

