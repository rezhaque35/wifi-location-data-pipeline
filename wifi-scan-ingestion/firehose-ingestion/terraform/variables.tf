// wifi-scan-ingestion/firehose-ingestion/terraform/variables.tf
// Variables for WiFi scan ingestion pipeline infrastructure

variable "aws_region" {
  description = "AWS region to use (default: us-east-1 for LocalStack)"
  type        = string
  default     = "us-east-1"
}

variable "aws_access_key" {
  description = "AWS access key (dummy for LocalStack)"
  type        = string
  default     = "test"
}

variable "aws_secret_key" {
  description = "AWS secret key (dummy for LocalStack)"
  type        = string
  default     = "test"
}

variable "localstack_endpoint" {
  description = "LocalStack endpoint URL (default: http://localhost:4566)"
  type        = string
  default     = "http://localhost:4566"
}

variable "environment" {
  description = "Environment name (localstack, dev, staging, prod)"
  type        = string
  default     = "localstack"
}
