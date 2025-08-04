// wifi-scan-ingestion/firehose-ingestion/terraform/modules/eventbridge/variables.tf
// Variables for EventBridge module

variable "rule_name" {
  description = "Name of the EventBridge rule"
  type        = string
  default     = "wifi-scan-s3-put-rule"
}

variable "s3_bucket_name" {
  description = "Name of the S3 bucket to monitor"
  type        = string
}

variable "sqs_queue_arn" {
  description = "ARN of the SQS queue target"
  type        = string
}

variable "eventbridge_role_arn" {
  description = "ARN of the IAM role for EventBridge"
  type        = string
}

variable "environment" {
  description = "Environment name (localstack, dev, staging, prod)"
  type        = string
  default     = "localstack"
} 