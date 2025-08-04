// wifi-scan-ingestion/firehose-ingestion/terraform/modules/iam/outputs.tf
// Outputs for IAM module

output "firehose_delivery_role_arn" {
  description = "The ARN of the IAM role for Firehose delivery"
  value       = aws_iam_role.firehose_delivery_role.arn
}

output "eventbridge_sqs_role_arn" {
  description = "The ARN of the IAM role for EventBridge to send messages to SQS"
  value       = aws_iam_role.eventbridge_sqs_role.arn
} 