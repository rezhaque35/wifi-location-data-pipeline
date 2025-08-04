// wifi-scan-ingestion/firehose-ingestion/terraform/modules/s3/variables.tf
// Variables for S3 module

variable "bucket_name" {
  description = "Name of the S3 bucket for ingested WiFi scan data"
  type        = string
  default     = "ingested-wifiscan-data"
}

variable "environment" {
  description = "Environment name (localstack, dev, staging, prod)"
  type        = string
  default     = "localstack"
} 