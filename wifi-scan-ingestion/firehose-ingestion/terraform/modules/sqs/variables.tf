// wifi-scan-ingestion/firehose-ingestion/terraform/modules/sqs/variables.tf
// Variables for SQS module

variable "queue_name" {
  description = "Name of the SQS queue"
  type        = string
  default     = "wifi_scan_ingestion_event_queue"
}

variable "visibility_timeout" {
  description = "Visibility timeout in seconds"
  type        = number
  default     = 30
}

variable "message_retention" {
  description = "Message retention period in seconds"
  type        = number
  default     = 1209600 # 14 days
}

variable "environment" {
  description = "Environment name (localstack, dev, staging, prod)"
  type        = string
  default     = "localstack"
} 