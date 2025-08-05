#!/bin/bash

# wifi-measurements-transformer-service/scripts/verify-setup.sh
# Verification script to check if LocalStack setup is working correctly

set -e

echo "🔍 Verifying LocalStack setup for WiFi Measurements Transformer Service..."

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

# Function to check LocalStack health
check_localstack_health() {
    echo "🔍 Checking LocalStack health..."
    if curl -s $LOCALSTACK_ENDPOINT/health > /dev/null; then
        echo "✅ LocalStack is running and healthy"
        return 0
    else
        echo "❌ LocalStack is not running or not healthy"
        return 1
    fi
}

# Function to verify SQS queues
verify_sqs_queues() {
    echo "🔍 Verifying SQS queues..."
    
    # Check main queue
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name $SQS_QUEUE_NAME > /dev/null 2>&1; then
        MAIN_QUEUE_URL=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name $SQS_QUEUE_NAME --query 'QueueUrl' --output text)
        echo "✅ Main queue exists: $MAIN_QUEUE_URL"
    else
        echo "❌ Main queue '$SQS_QUEUE_NAME' not found"
        return 1
    fi
    
    # Check DLQ
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name "${SQS_QUEUE_NAME}-dlq" > /dev/null 2>&1; then
        DLQ_URL=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name "${SQS_QUEUE_NAME}-dlq" --query 'QueueUrl' --output text)
        echo "✅ DLQ exists: $DLQ_URL"
    else
        echo "❌ DLQ '${SQS_QUEUE_NAME}-dlq' not found"
        return 1
    fi
}

# Function to verify S3 buckets
verify_s3_buckets() {
    echo "🔍 Verifying S3 buckets..."
    
    # Check ingestion bucket
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_BUCKET_NAME > /dev/null 2>&1; then
        echo "✅ Ingestion bucket exists: s3://$S3_BUCKET_NAME"
    else
        echo "❌ Ingestion bucket 's3://$S3_BUCKET_NAME' not found"
        return 1
    fi
    
    # Check warehouse bucket
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_WAREHOUSE_BUCKET > /dev/null 2>&1; then
        echo "✅ Warehouse bucket exists: s3://$S3_WAREHOUSE_BUCKET"
    else
        echo "❌ Warehouse bucket 's3://$S3_WAREHOUSE_BUCKET' not found"
        return 1
    fi
    
    # Check Firehose destination bucket
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_FIREHOSE_DESTINATION_BUCKET > /dev/null 2>&1; then
        echo "✅ Firehose destination bucket exists: s3://$S3_FIREHOSE_DESTINATION_BUCKET"
    else
        echo "❌ Firehose destination bucket 's3://$S3_FIREHOSE_DESTINATION_BUCKET' not found"
        return 1
    fi
}

# Function to verify Firehose delivery stream
verify_firehose() {
    echo "🔍 Verifying Firehose delivery stream..."
    
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose describe-delivery-stream --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME > /dev/null 2>&1; then
        echo "✅ Firehose delivery stream exists: $FIREHOSE_DELIVERY_STREAM_NAME"
    else
        echo "❌ Firehose delivery stream '$FIREHOSE_DELIVERY_STREAM_NAME' not found"
        return 1
    fi
}

# Function to verify application configuration
verify_application_config() {
    echo "🔍 Verifying application configuration..."
    
    # Check if application.yml exists
    if [ ! -f "src/main/resources/application.yml" ]; then
        echo "❌ application.yml not found"
        return 1
    fi
    
    # Check SQS queue URL configuration
    EXPECTED_QUEUE_URL="http://localhost:4566/000000000000/wifi-scan-events"
    CONFIGURED_QUEUE_URL=$(grep "queue-url:" src/main/resources/application.yml | head -1 | awk '{print $2}')
    
    if [ "$CONFIGURED_QUEUE_URL" = "$EXPECTED_QUEUE_URL" ]; then
        echo "✅ SQS queue URL configuration matches: $CONFIGURED_QUEUE_URL"
    else
        echo "❌ SQS queue URL configuration mismatch"
        echo "   Expected: $EXPECTED_QUEUE_URL"
        echo "   Found: $CONFIGURED_QUEUE_URL"
        return 1
    fi
    
    # Check LocalStack endpoint configuration
    EXPECTED_ENDPOINT="http://localhost:4566"
    CONFIGURED_ENDPOINT=$(grep "endpoint-url:" src/main/resources/application.yml | head -1 | awk '{print $2}')
    
    if [ "$CONFIGURED_ENDPOINT" = "$EXPECTED_ENDPOINT" ]; then
        echo "✅ LocalStack endpoint configuration matches: $CONFIGURED_ENDPOINT"
    else
        echo "❌ LocalStack endpoint configuration mismatch"
        echo "   Expected: $EXPECTED_ENDPOINT"
        echo "   Found: $CONFIGURED_ENDPOINT"
        return 1
    fi
}

# Function to display verification summary
display_summary() {
    echo ""
    echo "🎉 Verification completed!"
    echo ""
    echo "📋 Summary:"
    echo "  ✅ LocalStack health check"
    echo "  ✅ SQS queues verification"
    echo "  ✅ S3 buckets verification"
    echo "  ✅ Firehose delivery stream verification"
    echo "  ✅ Application configuration verification"
    echo ""
    echo "🚀 Your setup is ready! You can now start the application:"
    echo "   mvn spring-boot:run -Dspring-boot.run.profiles=local"
    echo ""
    echo "🔗 Useful URLs:"
    echo "   LocalStack Health: $LOCALSTACK_ENDPOINT/health"
    echo "   SQS Console: $LOCALSTACK_ENDPOINT/_localstack/sqs"
    echo "   S3 Console: $LOCALSTACK_ENDPOINT/_localstack/s3"
    echo "   Firehose Console: $LOCALSTACK_ENDPOINT/_localstack/firehose"
}

# Function to display error summary
display_error_summary() {
    echo ""
    echo "❌ Verification failed!"
    echo ""
    echo "🔧 To fix issues:"
    echo "   1. Run cleanup: ./scripts/cleanup.sh --force"
    echo "   2. Run setup: ./scripts/setup.sh"
    echo "   3. Run verification again: ./scripts/verify-setup.sh"
    echo ""
    echo "📖 For more help, check the troubleshooting section in README.md"
}

# Main execution
main() {
    local all_passed=true
    
    # Check LocalStack health
    if ! check_localstack_health; then
        all_passed=false
    fi
    
    # Verify SQS queues
    if ! verify_sqs_queues; then
        all_passed=false
    fi
    
    # Verify S3 buckets
    if ! verify_s3_buckets; then
        all_passed=false
    fi
    
    # Verify Firehose delivery stream
    if ! verify_firehose; then
        all_passed=false
    fi
    
    # Verify application configuration
    if ! verify_application_config; then
        all_passed=false
    fi
    
    # Display appropriate summary
    if [ "$all_passed" = true ]; then
        display_summary
        exit 0
    else
        display_error_summary
        exit 1
    fi
}

# Execute main function
main "$@" 