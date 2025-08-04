// wifi-scan-ingestion/firehose-ingestion/terraform/modules/s3/main.tf
// S3 module for WiFi scan data ingestion pipeline

resource "aws_s3_bucket" "ingested_wifiscan_data" {
  bucket = var.bucket_name

  tags = {
    Name        = var.bucket_name
    Environment = var.environment
    Project     = "wifi-scan-ingestion"
  }
}

resource "aws_s3_bucket_versioning" "ingested_wifiscan_data_versioning" {
  bucket = aws_s3_bucket.ingested_wifiscan_data.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "ingested_wifiscan_data_encryption" {
  bucket = aws_s3_bucket.ingested_wifiscan_data.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_notification" "ingested_wifiscan_data_notification" {
  bucket      = aws_s3_bucket.ingested_wifiscan_data.id
  eventbridge = true
} 