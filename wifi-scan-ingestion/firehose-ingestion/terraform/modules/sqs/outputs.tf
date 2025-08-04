// wifi-scan-ingestion/firehose-ingestion/terraform/modules/sqs/outputs.tf
// Outputs for SQS module

output "queue_arn" {
  description = "ARN of the SQS queue"
  value       = aws_sqs_queue.wifi_scan_ingestion_queue.arn
}

output "queue_url" {
  description = "URL of the SQS queue"
  value       = aws_sqs_queue.wifi_scan_ingestion_queue.url
}

output "dlq_arn" {
  description = "ARN of the dead letter queue"
  value       = aws_sqs_queue.wifi_scan_ingestion_dlq.arn
}

output "queue_name" {
  description = "Name of the SQS queue"
  value       = aws_sqs_queue.wifi_scan_ingestion_queue.name
} 