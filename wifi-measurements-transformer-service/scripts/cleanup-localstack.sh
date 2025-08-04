#!/bin/bash

# wifi-measurements-transformer-service/scripts/cleanup-localstack.sh
# Cleanup script for LocalStack AWS infrastructure

set -e

echo "🧹 Cleaning up LocalStack infrastructure for WiFi Measurements Transformer Service..."

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

# Function to check if LocalStack is running
check_localstack() {
    echo "🔍 Checking if LocalStack is running..."
    if ! curl -s $LOCALSTACK_ENDPOINT/health > /dev/null; then
        echo "⚠️  LocalStack is not running. Nothing to clean up."
        exit 0
    fi
    echo "✅ LocalStack is running"
}

# Function to delete Kinesis Data Firehose resources
cleanup_firehose() {
    echo "🔧 Cleaning up Kinesis Data Firehose resources..."
    
    # Delete the delivery stream
    aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose delete-delivery-stream \
        --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME 2>/dev/null || echo "Firehose delivery stream may not exist"
    
    echo "✅ Kinesis Data Firehose delivery stream cleaned up"
}

# Function to delete EventBridge resources
cleanup_eventbridge() {
    echo "🔧 Cleaning up EventBridge resources..."
    
    # Remove targets from the rule
    aws --endpoint-url=$LOCALSTACK_ENDPOINT events remove-targets \
        --rule "s3-object-created-rule" \
        --ids "1" 2>/dev/null || echo "EventBridge targets may not exist"
    
    # Delete the rule
    aws --endpoint-url=$LOCALSTACK_ENDPOINT events delete-rule \
        --name "s3-object-created-rule" 2>/dev/null || echo "EventBridge rule may not exist"
    
    echo "✅ EventBridge resources cleaned up"
}

# Function to delete S3 buckets and contents
cleanup_s3_buckets() {
    echo "🔧 Cleaning up S3 buckets..."
    
    # Delete all objects in ingestion bucket
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rm s3://$S3_BUCKET_NAME --recursive 2>/dev/null || echo "Ingestion bucket may not exist or be empty"
    
    # Delete all objects in warehouse bucket  
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rm s3://$S3_WAREHOUSE_BUCKET --recursive 2>/dev/null || echo "Warehouse bucket may not exist or be empty"
    
    # Delete all objects in Firehose destination bucket
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rm s3://$S3_FIREHOSE_DESTINATION_BUCKET --recursive 2>/dev/null || echo "Firehose destination bucket may not exist or be empty"
    
    # Delete the buckets
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rb s3://$S3_BUCKET_NAME 2>/dev/null || echo "Ingestion bucket may not exist"
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rb s3://$S3_WAREHOUSE_BUCKET 2>/dev/null || echo "Warehouse bucket may not exist"
    aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rb s3://$S3_FIREHOSE_DESTINATION_BUCKET 2>/dev/null || echo "Firehose destination bucket may not exist"
    
    echo "✅ S3 buckets cleaned up"
}

# Function to delete SQS queues
cleanup_sqs_queues() {
    echo "🔧 Cleaning up SQS queues..."
    
    # Get queue URLs
    MAIN_QUEUE_URL=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name $SQS_QUEUE_NAME --query 'QueueUrl' --output text 2>/dev/null || echo "")
    DLQ_URL=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name "${SQS_QUEUE_NAME}-dlq" --query 'QueueUrl' --output text 2>/dev/null || echo "")
    
    # Delete main queue
    if [ ! -z "$MAIN_QUEUE_URL" ] && [ "$MAIN_QUEUE_URL" != "None" ]; then
        aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs delete-queue --queue-url "$MAIN_QUEUE_URL"
        echo "✅ Main SQS queue deleted"
    else
        echo "ℹ️  Main SQS queue does not exist"
    fi
    
    # Delete DLQ
    if [ ! -z "$DLQ_URL" ] && [ "$DLQ_URL" != "None" ]; then
        aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs delete-queue --queue-url "$DLQ_URL"
        echo "✅ DLQ deleted"
    else
        echo "ℹ️  DLQ does not exist"
    fi
    
    echo "✅ SQS queues cleaned up"
}

# Function to clean up logs directory
cleanup_logs() {
    echo "🔧 Cleaning up local logs..."
    
    if [ -d "logs" ]; then
        rm -rf logs/
        echo "✅ Local logs directory cleaned up"
    else
        echo "ℹ️  No logs directory to clean up"
    fi
}

# Function to display cleanup summary
display_summary() {
    echo ""
    echo "🎉 LocalStack cleanup completed successfully!"
    echo ""
    echo "📋 Cleaned up resources:"
    echo "  ❌ Kinesis Data Firehose delivery stream"
    echo "  ❌ EventBridge rules and targets"
    echo "  ❌ S3 buckets and all contents (including Firehose destination)"
    echo "  ❌ SQS queues (main and DLQ)"
    echo "  ❌ Local logs directory"
    echo ""
    echo "🔗 To verify cleanup:"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs list-queues"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT events list-rules"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose list-delivery-streams"
    echo ""
    echo "🔄 To reset the environment, run:"
    echo "  ./scripts/setup-localstack.sh"
}

# Function to prompt for confirmation
confirm_cleanup() {
    read -p "⚠️  This will delete ALL LocalStack resources for this service (including Firehose). Continue? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "❌ Cleanup cancelled"
        exit 1
    fi
}

# Main execution
main() {
    if [ "$1" != "--force" ]; then
        confirm_cleanup
    fi
    
    check_localstack
    cleanup_firehose
    cleanup_eventbridge
    cleanup_s3_buckets
    cleanup_sqs_queues
    cleanup_logs
    display_summary
}

# Execute main function
main "$@" 