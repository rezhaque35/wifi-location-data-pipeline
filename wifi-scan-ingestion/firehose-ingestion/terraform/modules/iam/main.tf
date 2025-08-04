// wifi-scan-ingestion/firehose-ingestion/terraform/modules/iam/main.tf
// IAM module for WiFi scan data ingestion pipeline

# Firehose delivery role
resource "aws_iam_role" "firehose_delivery_role" {
  name = "firehose-delivery-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "firehose.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "firehose-delivery-role"
    Environment = var.environment
    Project     = "wifi-scan-ingestion"
  }
}

# Firehose delivery policy
resource "aws_iam_role_policy" "firehose_delivery_policy" {
  name = "firehose-delivery-policy"
  role = aws_iam_role.firehose_delivery_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:AbortMultipartUpload",
          "s3:GetBucketLocation",
          "s3:GetObject",
          "s3:ListBucket",
          "s3:ListBucketMultipartUploads",
          "s3:PutObject"
        ]
        Resource = [
          var.s3_bucket_arn,
          "${var.s3_bucket_arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

# S3 to EventBridge role
resource "aws_iam_role" "s3_eventbridge_role" {
  name = "s3-eventbridge-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "s3.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "s3-eventbridge-role"
    Environment = var.environment
    Project     = "wifi-scan-ingestion"
  }
}

# S3 to EventBridge policy
resource "aws_iam_role_policy" "s3_eventbridge_policy" {
  name = "s3-eventbridge-policy"
  role = aws_iam_role.s3_eventbridge_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "events:PutEvents"
        ]
        Resource = "*"
      }
    ]
  })
}

# EventBridge to SQS role
resource "aws_iam_role" "eventbridge_sqs_role" {
  name = "eventbridge-sqs-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "eventbridge-sqs-role"
    Environment = var.environment
    Project     = "wifi-scan-ingestion"
  }
}

# EventBridge to SQS policy
resource "aws_iam_role_policy" "eventbridge_sqs_policy" {
  name = "eventbridge-sqs-policy"
  role = aws_iam_role.eventbridge_sqs_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage"
        ]
        Resource = var.sqs_queue_arn
      }
    ]
  })
} 