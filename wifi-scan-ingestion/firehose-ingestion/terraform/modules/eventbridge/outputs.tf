// wifi-scan-ingestion/firehose-ingestion/terraform/modules/eventbridge/outputs.tf
// Outputs for EventBridge module

output "rule_name" {
  description = "Name of the EventBridge rule"
  value       = aws_cloudwatch_event_rule.wifi_scan_s3_put_rule.name
}

output "rule_arn" {
  description = "ARN of the EventBridge rule"
  value       = aws_cloudwatch_event_rule.wifi_scan_s3_put_rule.arn
} 