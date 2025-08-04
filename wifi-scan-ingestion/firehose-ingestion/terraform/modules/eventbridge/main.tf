// wifi-scan-ingestion/firehose-ingestion/terraform/modules/eventbridge/main.tf
// EventBridge module for WiFi scan data ingestion pipeline

resource "aws_cloudwatch_event_rule" "wifi_scan_s3_put_rule" {
  name        = var.rule_name
  description = "Trigger on S3 object creation for WiFi scan data"

  event_pattern = jsonencode({
    source      = ["aws.s3"]
    detail-type = ["Object Created"]
    detail = {
      bucket = {
        name = [var.s3_bucket_name]
      }
      object = {
        key = [{
          suffix = ".txt"
        }]
      }
    }
  })

  tags = {
    Name        = var.rule_name
    Environment = var.environment
    Project     = "wifi-scan-ingestion"
  }
}

resource "aws_cloudwatch_event_target" "wifi_scan_sqs_target" {
  rule      = aws_cloudwatch_event_rule.wifi_scan_s3_put_rule.name
  target_id = "WiFiScanSQSTarget"
  arn       = var.sqs_queue_arn
  
  role_arn = var.eventbridge_role_arn
} 