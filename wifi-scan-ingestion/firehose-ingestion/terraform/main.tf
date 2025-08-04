// wifi-scan-ingestion/firehose-ingestion/terraform/main.tf
// Terraform main entry point for WiFi scan ingestion pipeline infrastructure

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 4.0"
    }
  }
  required_version = ">= 1.0"
}

provider "aws" {
  region                      = var.aws_region
  access_key                  = var.aws_access_key
  secret_key                  = var.aws_secret_key

  s3_use_path_style           = true
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    s3       = var.localstack_endpoint
    kinesis  = var.localstack_endpoint
    firehose = var.localstack_endpoint
    sqs      = var.localstack_endpoint
    iam      = var.localstack_endpoint
    events   = var.localstack_endpoint
    logs     = var.localstack_endpoint
  }
}

# S3 module
module "s3" {
  source = "./modules/s3"
  
  bucket_name = "ingested-wifiscan-data"
  environment = var.environment
}

# SQS module
module "sqs" {
  source = "./modules/sqs"
  
  queue_name         = "wifi_scan_ingestion_event_queue"
  visibility_timeout = 30
  message_retention  = 1209600
  environment        = var.environment
}

# IAM module (depends on S3 and SQS)
module "iam" {
  source = "./modules/iam"
  
  environment    = var.environment
  s3_bucket_arn  = module.s3.bucket_arn
  sqs_queue_arn  = module.sqs.queue_arn
}

# Firehose module (depends on S3 and IAM)
module "firehose" {
  source = "./modules/firehose"
  
  stream_name        = "MVS-stream"
  firehose_role_arn  = module.iam.firehose_delivery_role_arn
  s3_bucket_arn      = module.s3.bucket_arn
  buffer_size        = 5
  buffer_interval    = 60
  compression_format = "GZIP"
  environment        = var.environment
}

# EventBridge module (depends on S3, SQS, and IAM)
module "eventbridge" {
  source = "./modules/eventbridge"
  
  rule_name             = "wifi-scan-s3-put-rule"
  s3_bucket_name        = module.s3.bucket_name
  sqs_queue_arn         = module.sqs.queue_arn
  eventbridge_role_arn  = module.iam.eventbridge_sqs_role_arn
  environment           = var.environment
}
