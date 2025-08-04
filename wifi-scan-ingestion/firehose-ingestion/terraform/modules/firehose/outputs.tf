// wifi-scan-ingestion/firehose-ingestion/terraform/modules/firehose/outputs.tf
// Outputs for Kinesis Firehose module

output "stream_name" {
  description = "Name of the Kinesis Firehose delivery stream"
  value       = aws_kinesis_firehose_delivery_stream.wifi_scan_ingestion_stream.name
}

output "stream_arn" {
  description = "ARN of the Kinesis Firehose delivery stream"
  value       = aws_kinesis_firehose_delivery_stream.wifi_scan_ingestion_stream.arn
} 