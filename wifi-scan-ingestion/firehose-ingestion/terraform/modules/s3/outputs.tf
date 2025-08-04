// wifi-scan-ingestion/firehose-ingestion/terraform/modules/s3/outputs.tf
// Outputs for S3 module

output "bucket_name" {
  description = "Name of the S3 bucket"
  value       = aws_s3_bucket.ingested_wifiscan_data.bucket
}

output "bucket_id" {
  description = "ID of the S3 bucket"
  value       = aws_s3_bucket.ingested_wifiscan_data.id
}

output "bucket_arn" {
  description = "ARN of the S3 bucket"
  value       = aws_s3_bucket.ingested_wifiscan_data.arn
} 