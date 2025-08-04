// wifi-scan-ingestion/firehose-ingestion/terraform/modules/firehose/variables.tf
// Variables for Kinesis Firehose module

variable "stream_name" {
  description = "Name of the Kinesis Firehose delivery stream"
  type        = string
  default     = "MVS-stream"
}

variable "firehose_role_arn" {
  description = "ARN of the IAM role for Firehose"
  type        = string
}

variable "s3_bucket_arn" {
  description = "ARN of the S3 bucket for Firehose destination"
  type        = string
}

variable "buffer_size" {
  description = "Buffer size in MB"
  type        = number
  default     = 5
}

variable "buffer_interval" {
  description = "Buffer interval in seconds"
  type        = number
  default     = 60
}

variable "compression_format" {
  description = "Compression format (GZIP, UNCOMPRESSED)"
  type        = string
  default     = "GZIP"
}

variable "environment" {
  description = "Environment name (localstack, dev, staging, prod)"
  type        = string
  default     = "localstack"
} 