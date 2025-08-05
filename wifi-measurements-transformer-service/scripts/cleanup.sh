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

# Function to stop and remove LocalStack Docker container
cleanup_localstack_container() {
    echo "🔧 Cleaning up LocalStack Docker container..."
    
    # Stop the LocalStack container if running
    if docker ps | grep -q "localstack-wifi-transformer"; then
        echo "🛑 Stopping LocalStack container..."
        docker stop localstack-wifi-transformer
        echo "✅ LocalStack container stopped"
    else
        echo "ℹ️  LocalStack container is not running"
    fi
    
    # Remove any stopped containers
    if docker ps -a | grep -q "localstack-wifi-transformer"; then
        echo "🗑️  Removing LocalStack container..."
        docker rm localstack-wifi-transformer
        echo "✅ LocalStack container removed"
    else
        echo "ℹ️  No LocalStack container to remove"
    fi
}

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
    echo "  ❌ LocalStack Docker container"
    echo ""
    echo "🔗 To verify cleanup:"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs list-queues"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT events list-rules"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose list-delivery-streams"
    echo "  docker ps -a | grep localstack-wifi-transformer"
    echo ""
    echo "🔄 To reset the environment, run:"
    echo "  ./scripts/setup.sh"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Options:"
    echo "  --force        - Cleanup without confirmation prompt"
    echo "  --remove-image - Also remove LocalStack Docker image (frees ~500MB)"
    echo "  --help         - Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Cleanup with confirmation prompt"
    echo "  $0 --force            # Cleanup without confirmation"
    echo "  $0 --remove-image     # Cleanup + remove Docker image"
    echo "  $0 --force --remove-image # Cleanup + remove image without confirmation"
    echo "  $0 --help             # Show this help message"
    echo ""
    echo "This script will clean up:"
    echo "  - All LocalStack resources (SQS, S3, Firehose, EventBridge)"
    echo "  - LocalStack Docker container"
    echo "  - Local logs directory"
    echo "  - All data and configurations"
    echo ""
    echo "Note: Docker image is preserved by default for offline development."
    echo "Use --remove-image to free up disk space (~500MB)."
}

# Function to remove LocalStack Docker image
remove_localstack_image() {
    echo "🔧 Removing LocalStack Docker image..."
    
    if docker images | grep -q "localstack/localstack"; then
        echo "🗑️  Removing LocalStack Docker image..."
        docker rmi localstack/localstack
        echo "✅ LocalStack Docker image removed (~500MB freed)"
    else
        echo "ℹ️  LocalStack Docker image not found"
    fi
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

# Function to create SQS queue (for reference in cleanup)
create_sqs_queue() {
    echo "🔧 Creating SQS queue: $SQS_QUEUE_NAME"
    
    # Create the main queue
    aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
        --queue-name $SQS_QUEUE_NAME \
        --attributes '{
            "VisibilityTimeout": "300",
            "MessageRetentionPeriod": "1209600",
            "DelaySeconds": "0",
            "ReceiveMessageWaitTimeSeconds": "20"
        }' || echo "Queue may already exist"
    
    # Create dead letter queue
    aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
        --queue-name "${SQS_QUEUE_NAME}-dlq" \
        --attributes '{
            "VisibilityTimeout": "300",
            "MessageRetentionPeriod": "1209600"
        }' || echo "DLQ may already exist"
    
    echo "✅ SQS queues created"
}

# Main execution
main() {
    local remove_image=false
    local force=false
    
    # Parse arguments
    for arg in "$@"; do
        case "$arg" in
            "--help"|"-h"|"help")
                show_usage
                exit 0
                ;;
            "--force")
                force=true
                ;;
            "--remove-image")
                remove_image=true
                ;;
            *)
                echo "❌ Unknown option: $arg"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # Require confirmation unless --force is used
    if [ "$force" = false ]; then
        confirm_cleanup
    fi
    
    check_localstack
    cleanup_firehose
    cleanup_eventbridge
    cleanup_s3_buckets
    cleanup_sqs_queues
    cleanup_logs
    cleanup_localstack_container
    display_summary
    
    # Remove image if requested
    if [ "$remove_image" = true ]; then
        remove_localstack_image
    fi
}

# Execute main function
main "$@" 