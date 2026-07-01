# ── X-Ray Transaction Search (account-level) ─────────────────────────────────
# The X-Ray OTLP endpoint (https://xray.<region>.amazonaws.com/v1/traces) only ingests spans once
# Transaction Search is enabled for the account. There is no native terraform-aws resource for this
# yet, so we wrap the AWS-documented CloudFormation directly:
#   1. AWS::Logs::ResourcePolicy         — lets the X-Ray service write spans into the aws/spans log group.
#   2. AWS::XRay::TransactionSearchConfig — flips the account into Transaction Search (CloudWatch Logs)
#                                           ingestion mode and sets how many spans are indexed.
# Fn::Sub resolves AWS::Partition / Region / AccountId at deploy time, so no TF interpolation needed.
#
# NOTE: account-level + singleton. CloudFormation requires Transaction Search to be DISABLED before
# this stack creates it; if it is already on, import or skip this module.
resource "aws_cloudformation_stack" "xray_transaction_search" {
  name = "cloudledger-${var.env}-xray-transaction-search"

  template_body = jsonencode({
    Resources = {
      LogsResourcePolicy = {
        Type = "AWS::Logs::ResourcePolicy"
        Properties = {
          PolicyName = "TransactionSearchAccess"
          # Fn::Sub so CloudFormation expands the AWS::Partition / Region / AccountId pseudo-params.
          PolicyDocument = {
            "Fn::Sub" = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"TransactionSearchXRayAccess\",\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"xray.amazonaws.com\"},\"Action\":\"logs:PutLogEvents\",\"Resource\":[\"arn:$${AWS::Partition}:logs:$${AWS::Region}:$${AWS::AccountId}:log-group:aws/spans:*\",\"arn:$${AWS::Partition}:logs:$${AWS::Region}:$${AWS::AccountId}:log-group:/aws/application-signals/data:*\"],\"Condition\":{\"ArnLike\":{\"aws:SourceArn\":\"arn:$${AWS::Partition}:xray:$${AWS::Region}:$${AWS::AccountId}:*\"},\"StringEquals\":{\"aws:SourceAccount\":\"$${AWS::AccountId}\"}}}]}"
          }
        }
      }
      XRayTransactionSearchConfig = {
        Type      = "AWS::XRay::TransactionSearchConfig"
        DependsOn = "LogsResourcePolicy"
        Properties = {
          IndexingPercentage = var.indexing_percentage
        }
      }
    }
  })

  tags = { Name = "cloudledger-${var.env}", Project = "cloud-ledger" }
}
