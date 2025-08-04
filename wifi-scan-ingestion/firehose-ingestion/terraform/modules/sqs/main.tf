// wifi-scan-ingestion/firehose-ingestion/terraform/modules/sqs/main.tf
// SQS module for WiFi scan data ingestion pipeline

# Dead letter queue
resource "aws_sqs_queue" "wifi_scan_ingestion_dlq" {
  name                      = "${var.queue_name}-dlq"
  message_retention_seconds = 1209600 # 14 days

  tags = {
    Name        = "${var.queue_name}-dlq"
    Environment = var.environment
    Project     = "wifi-scan-ingestion"
  }
}

# Main queue
resource "aws_sqs_queue" "wifi_scan_ingestion_queue" {
  name                      = var.queue_name
  visibility_timeout_seconds = var.visibility_timeout
  message_retention_seconds = var.message_retention
  
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.wifi_scan_ingestion_dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Name        = var.queue_name
    Environment = var.environment
    Project     = "wifi-scan-ingestion"
  }
} 