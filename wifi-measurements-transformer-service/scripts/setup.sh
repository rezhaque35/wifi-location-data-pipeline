#!/bin/bash

# wifi-measurements-transformer-service/scripts/setup-localstack.sh
# Setup script for LocalStack AWS infrastructure for local development

set -e

echo "🚀 Setting up LocalStack infrastructure for WiFi Measurements Transformer Service..."

# Configuration
LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
SQS_QUEUE_NAME="wifi-scan-events"
S3_BUCKET_NAME="ingested-wifiscan-data"
S3_WAREHOUSE_BUCKET="wifi-measurements-warehouse"
S3_FIREHOSE_DESTINATION_BUCKET="wifi-measurements-table"
FIREHOSE_DELIVERY_STREAM_NAME="wifi-measurements-stream"

# AWS CLI configuration for LocalStack
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="$AWS_REGION"

# Function to check if LocalStack is running and start it if needed
check_localstack() {
    echo "🔍 Checking if LocalStack is running..."
    if ! curl -s $LOCALSTACK_ENDPOINT/health > /dev/null; then
        echo "❌ LocalStack is not running. Starting LocalStack..."
        
        # Check if Docker is available
        if ! command -v docker &> /dev/null; then
            echo "❌ Docker is not installed or not in PATH. Please install Docker first."
            exit 1
        fi
        
        # Check if LocalStack image exists, pull if not
        if ! docker images | grep -q "localstack/localstack"; then
            echo "📥 Pulling LocalStack image..."
            docker pull localstack/localstack
        fi
        
        # Start LocalStack in background
        echo "🚀 Starting LocalStack container..."
        docker run --rm -d \
            --name localstack-wifi-transformer \
            -p 4566:4566 \
            -p 4510-4559:4510-4559 \
            -e SERVICES=s3,sqs,firehose,events,iam,sts \
            -e DEBUG=1 \
            -e DATA_DIR=/tmp/localstack/data \
            localstack/localstack
        
        # Wait for LocalStack to be ready
        echo "⏳ Waiting for LocalStack to be ready..."
        local max_attempts=30
        local attempt=1
        
        while [ $attempt -le $max_attempts ]; do
            if curl -s $LOCALSTACK_ENDPOINT/health > /dev/null; then
                echo "✅ LocalStack is ready!"
                break
            fi
            
            echo "   Attempt $attempt/$max_attempts - waiting..."
            sleep 2
            attempt=$((attempt + 1))
        done
        
        if [ $attempt -gt $max_attempts ]; then
            echo "❌ LocalStack failed to start within expected time"
            echo "   You can check the logs with: docker logs localstack-wifi-transformer"
            exit 1
        fi
    else
        echo "✅ LocalStack is already running"
    fi
}

# Function to create SQS queue
create_sqs_queue() {
    echo "🔧 Creating SQS queue: $SQS_QUEUE_NAME"
    
    # Create the main queue
    echo "   Creating main queue..."
    aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
        --queue-name $SQS_QUEUE_NAME \
        --attributes '{
            "VisibilityTimeout": "300",
            "MessageRetentionPeriod": "1209600",
            "DelaySeconds": "0",
            "ReceiveMessageWaitTimeSeconds": "20"
        }' || {
        echo "❌ Failed to create main SQS queue"
        return 1
    }
    
    # Create dead letter queue
    echo "   Creating dead letter queue..."
    aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
        --queue-name "${SQS_QUEUE_NAME}-dlq" \
        --attributes '{
            "VisibilityTimeout": "300",
            "MessageRetentionPeriod": "1209600"
        }' || {
        echo "❌ Failed to create DLQ"
        return 1
    }
    
    # Verify queues were created
    echo "   Verifying queue creation..."
    sleep 2  # Give LocalStack time to process
    
    # Check main queue
    if ! aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name $SQS_QUEUE_NAME > /dev/null 2>&1; then
        echo "❌ Main queue verification failed"
        return 1
    fi
    
    # Check DLQ
    if ! aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name "${SQS_QUEUE_NAME}-dlq" > /dev/null 2>&1; then
        echo "❌ DLQ verification failed"
        return 1
    fi
    
    # Get and display queue URLs
    MAIN_QUEUE_URL=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name $SQS_QUEUE_NAME --query 'QueueUrl' --output text)
    DLQ_URL=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name "${SQS_QUEUE_NAME}-dlq" --query 'QueueUrl' --output text)
    
    echo "✅ SQS queues created successfully"
    echo "   Main Queue URL: $MAIN_QUEUE_URL"
    echo "   DLQ URL: $DLQ_URL"
}

# Function to create S3 buckets
create_s3_buckets() {
    echo "🔧 Creating S3 buckets..."
    
    # Create ingestion bucket
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://$S3_BUCKET_NAME || echo "Bucket may already exist"
    
    # Create warehouse bucket for Iceberg
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://$S3_WAREHOUSE_BUCKET || echo "Warehouse bucket may already exist"
    
    # Create Firehose destination bucket
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://$S3_FIREHOSE_DESTINATION_BUCKET || echo "Firehose destination bucket may already exist"
    
    # Enable versioning on buckets
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3api put-bucket-versioning \
        --bucket $S3_BUCKET_NAME \
        --versioning-configuration Status=Enabled
    
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3api put-bucket-versioning \
        --bucket $S3_WAREHOUSE_BUCKET \
        --versioning-configuration Status=Enabled
    
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3api put-bucket-versioning \
        --bucket $S3_FIREHOSE_DESTINATION_BUCKET \
        --versioning-configuration Status=Enabled
    
    echo "✅ S3 buckets created"
}

# Function to setup Kinesis Data Firehose
setup_firehose() {
    echo "🔧 Setting up Kinesis Data Firehose delivery stream..."
    
    # Create Firehose delivery stream with S3 destination
    aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose create-delivery-stream \
        --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME \
        --delivery-stream-type "DirectPut" \
        --extended-s3-destination-configuration '{
            "RoleARN": "arn:aws:iam::000000000000:role/firehose-role",
            "BucketARN": "arn:aws:s3:::'$S3_FIREHOSE_DESTINATION_BUCKET'",
            "Prefix": "wifi-measurements/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/",
            "ErrorOutputPrefix": "errors/!{firehose:error-output-type}/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/",
            "BufferingHints": {
                "SizeInMBs": 148,
                "IntervalInSeconds": 60
            },
            "CompressionFormat": "GZIP",
            "EncryptionConfiguration": {
                "NoEncryptionConfig": "NoEncryption"
            },
            "CloudWatchLoggingOptions": {
                "Enabled": true,
                "LogGroupName": "/aws/firehose/'$FIREHOSE_DELIVERY_STREAM_NAME'",
                "LogStreamName": "DestinationDelivery"
            }
        }' || echo "Firehose delivery stream may already exist"
    
    echo "✅ Kinesis Data Firehose delivery stream created"
}

# Function to setup EventBridge rule for S3 events
setup_eventbridge() {
    echo "🔧 Setting up EventBridge for S3 events..."
    
    # Create event rule for S3 object creation
    aws --endpoint-url=$LOCALSTACK_ENDPOINT events put-rule \
        --name "s3-object-created-rule" \
        --event-pattern '{
            "source": ["aws.s3"],
            "detail-type": ["Object Created"],
            "detail": {
                "bucket": {
                    "name": ["'$S3_BUCKET_NAME'"]
                }
            }
        }' || echo "EventBridge rule may already exist"
    
    # Add SQS target to the rule
    QUEUE_ARN="arn:aws:sqs:$AWS_REGION:000000000000:$SQS_QUEUE_NAME"
    aws --endpoint-url=$LOCALSTACK_ENDPOINT events put-targets \
        --rule "s3-object-created-rule" \
        --targets "Id"="1","Arn"="$QUEUE_ARN" || echo "EventBridge target may already exist"
    
    echo "✅ EventBridge configured"
}

# Function to create sample test data
create_sample_data() {
    echo "🔧 Creating sample test data..."
    
    # Create a sample WiFi scan file
    cat > /tmp/sample-wifi-scan.json << 'EOF'
{
  "osVersion": "14:samsung/a53xsqw/a53x:14/UP1A.231005.007/A536VSQSADXC1:user/release-keys",
  "model": "SM-A536V",
  "device": "a53x",
  "manufacturer": "samsung",
  "osName": "Android",
  "sdkInt": "34",
  "appNameVersion": "com.verizon.wifiloc.app/0.1.0.10000",
  "dataVersion": "15",
  "wifiConnectedEvents": [
    {
      "timestamp": 1731091615562,
      "eventId": "9a930a02-f0cc-4e6d-9b95-c18b4d5a542a",
      "eventType": "CONNECTED",
      "wifiConnectedInfo": {
        "bssid": "b8:f8:53:c0:1e:ff",
        "ssid": "TestNetwork",
        "linkSpeed": 351,
        "frequency": 5660,
        "rssi": -58
      },
      "location": {
        "latitude": 40.6768816,
        "longitude": -74.416391,
        "accuracy": 10.0,
        "time": 1731091614415
      }
    }
  ],
  "scanResults": [
    {
      "timestamp": 1731091615562,
      "location": {
        "latitude": 40.6768816,
        "longitude": -74.416391,
        "accuracy": 10.0,
        "time": 1731091614415
      },
      "results": [
        {
          "ssid": "TestNetwork",
          "bssid": "b8:f8:53:c0:1e:ff",
          "scantime": 1731091613712,
          "rssi": -61
        }
      ]
    }
  ]
}
EOF

    # Compress and encode the sample data (simulating the real format)
    gzip -c /tmp/sample-wifi-scan.json | base64 -w 0 > /tmp/sample-encoded.txt
    
    # Upload to S3
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 cp /tmp/sample-encoded.txt \
        s3://$S3_BUCKET_NAME/test-stream/2024/01/01/12/test-stream-2024-01-01-12-00-00-sample.txt
    
    # Clean up temp files
    rm -f /tmp/sample-wifi-scan.json /tmp/sample-encoded.txt
    
    echo "✅ Sample test data created"
}

# Function to verify all resources
verify_setup() {
    echo "🔍 Verifying all resources are properly created..."
    
    # Verify SQS queues
    echo "   Verifying SQS queues..."
    if ! aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name $SQS_QUEUE_NAME > /dev/null 2>&1; then
        echo "❌ Main SQS queue verification failed"
        return 1
    fi
    
    if ! aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name "${SQS_QUEUE_NAME}-dlq" > /dev/null 2>&1; then
        echo "❌ DLQ verification failed"
        return 1
    fi
    
    # Verify S3 buckets
    echo "   Verifying S3 buckets..."
    if ! aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_BUCKET_NAME > /dev/null 2>&1; then
        echo "❌ S3 ingestion bucket verification failed"
        return 1
    fi
    
    if ! aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_WAREHOUSE_BUCKET > /dev/null 2>&1; then
        echo "❌ S3 warehouse bucket verification failed"
        return 1
    fi
    
    if ! aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_FIREHOSE_DESTINATION_BUCKET > /dev/null 2>&1; then
        echo "❌ S3 Firehose destination bucket verification failed"
        return 1
    fi
    
    # Verify Firehose delivery stream
    echo "   Verifying Firehose delivery stream..."
    if ! aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose describe-delivery-stream --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME > /dev/null 2>&1; then
        echo "❌ Firehose delivery stream verification failed"
        return 1
    fi
    
    echo "✅ All resources verified successfully"
}

# Function to verify Firehose setup
verify_firehose() {
    echo "🔍 Verifying Firehose setup..."
    
    # Check if delivery stream exists
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose describe-delivery-stream \
        --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME > /dev/null 2>&1; then
        echo "✅ Firehose delivery stream '$FIREHOSE_DELIVERY_STREAM_NAME' is active"
    else
        echo "❌ Firehose delivery stream '$FIREHOSE_DELIVERY_STREAM_NAME' not found or not active"
        return 1
    fi
    
    # List all delivery streams
    echo "📋 Available Firehose delivery streams:"
    aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose list-delivery-streams || echo "No delivery streams found"
    
    echo "✅ Firehose verification completed"
}

# Function to display setup summary
display_summary() {
    echo ""
    echo "🎉 LocalStack setup completed successfully!"
    echo ""
    echo "📋 Configuration Summary:"
    echo "  LocalStack Endpoint: $LOCALSTACK_ENDPOINT"
    echo "  SQS Queue URL: $LOCALSTACK_ENDPOINT/000000000000/$SQS_QUEUE_NAME"
    echo "  S3 Ingestion Bucket: s3://$S3_BUCKET_NAME"
    echo "  S3 Warehouse Bucket: s3://$S3_WAREHOUSE_BUCKET"
    echo "  S3 Firehose Destination: s3://$S3_FIREHOSE_DESTINATION_BUCKET"
    echo "  Firehose Delivery Stream: $FIREHOSE_DELIVERY_STREAM_NAME"
    echo "  Firehose Buffer Size: 148 MB"
    echo "  Firehose Buffer Interval: 60 seconds"
    echo ""
    echo "🔗 Useful LocalStack URLs:"
    echo "  Health Check: $LOCALSTACK_ENDPOINT/health"
    echo "  SQS Console: $LOCALSTACK_ENDPOINT/_localstack/sqs"
    echo "  S3 Console: $LOCALSTACK_ENDPOINT/_localstack/s3"
    echo "  Firehose Console: $LOCALSTACK_ENDPOINT/_localstack/firehose"
    echo ""
    echo "🚦 To verify setup:"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs list-queues"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose list-delivery-streams"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose describe-delivery-stream --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME"
    echo ""
    echo "📊 Firehose Configuration Details:"
    echo "  - Delivery Stream Type: DirectPut"
    echo "  - Destination: S3 (s3://$S3_FIREHOSE_DESTINATION_BUCKET)"
    echo "  - Buffer Size: 148 MB"
    echo "  - Buffer Interval: 60 seconds"
    echo "  - Compression: GZIP"
    echo "  - Partitioning: year/month/day/hour"
    echo "  - Error Output: errors/ directory"
    echo ""
    echo "▶️  You can now start the WiFi Measurements Transformer Service with profile 'local'"
    echo "   The service will write to Firehose delivery stream: $FIREHOSE_DELIVERY_STREAM_NAME"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  setup     - Setup LocalStack infrastructure (default)"
    echo "  start     - Start LocalStack only"
    echo "  help      - Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0              # Setup infrastructure (start LocalStack if needed)"
    echo "  $0 setup        # Same as above"
    echo "  $0 start        # Start LocalStack only"
    echo ""
    echo "For cleanup operations, use: ./scripts/cleanup.sh"
}

# Main execution
main() {
    case "${1:-setup}" in
        "setup")
            check_localstack
            create_sqs_queue
            create_s3_buckets
            setup_firehose
            setup_eventbridge
            create_sample_data
            verify_setup
            display_summary
            ;;
        "start")
            check_localstack
            ;;
        "help"|"-h"|"--help")
            show_usage
            ;;
        *)
            echo "❌ Unknown command: $1"
            show_usage
            exit 1
            ;;
    esac
}

# Execute main function
main "$@" 