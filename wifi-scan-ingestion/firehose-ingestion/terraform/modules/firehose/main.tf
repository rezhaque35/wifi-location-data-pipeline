// wifi-scan-ingestion/firehose-ingestion/terraform/modules/firehose/main.tf
// Kinesis Firehose module for WiFi scan data ingestion pipeline

resource "aws_kinesis_firehose_delivery_stream" "wifi_scan_ingestion_stream" {
  name        = var.stream_name
  destination = "extended_s3"

  extended_s3_configuration {
    role_arn   = var.firehose_role_arn
    bucket_arn = var.s3_bucket_arn
    prefix     = "MVS-stream/"
    
    buffering_size     = var.buffer_size
    buffering_interval = var.buffer_interval
    compression_format = "UNCOMPRESSED"
    file_extension     = ".txt"
    
    cloudwatch_logging_options {
      enabled = true
    }
  }

  tags = {
    Name        = var.stream_name
    Environment = var.environment
    Project     = "wifi-scan-ingestion"
  }
} 