// wifi-scan-ingestion/firehose-ingestion/terraform/modules/iam/variables.tf
// Variables for IAM module

variable "environment" {
  description = "Environment name (localstack, dev, staging, prod)"
  type        = string
  default     = "localstack"
}

variable "s3_bucket_arn" {
  description = "ARN of the S3 bucket for firehose permissions"
  type        = string
}

variable "sqs_queue_arn" {
  description = "ARN of the SQS queue for EventBridge permissions"
  type        = string
} 