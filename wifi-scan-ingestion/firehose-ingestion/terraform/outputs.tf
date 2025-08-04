// wifi-scan-ingestion/firehose-ingestion/terraform/outputs.tf
// Outputs for WiFi scan ingestion pipeline infrastructure

output "s3_bucket_name" {
  description = "Name of the S3 bucket"
  value       = module.s3.bucket_name
}

output "firehose_stream_name" {
  description = "Name of the Kinesis Firehose delivery stream"
  value       = module.firehose.stream_name
}

output "sqs_queue_url" {
  description = "URL of the SQS queue"
  value       = module.sqs.queue_url
}

output "eventbridge_rule_name" {
  description = "Name of the EventBridge rule"
  value       = module.eventbridge.rule_name
}
