#!/bin/bash

# setup-ingestion-store-stage.sh
# Combined setup script for ingestion and store stages of the data pipeline
# Flow: Kafka -> Queue Consumer -> Firehose (MVS) -> S3 (wifi-scan) -> S3 Event -> SQS -> Transformer -> Firehose (measurements) -> S3

set -e

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT_SCRIPTS_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TRANSFORMER_SERVICE_DIR="$PROJECT_ROOT/../wifi-measurements-transformer-service"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Configuration
LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"

# S3 buckets (only 2 needed - LocalStack doesn't support Iceberg)
INGESTION_S3_BUCKET="wifi-scan-data-bucket"
OUTPUT_S3_BUCKET="wifi-measurements-table"

# Parse command line arguments
SKIP_INGESTION=false
SKIP_STORE=false
FORCE_CERTS=false
CLEANUP_FIRST=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-ingestion)
            SKIP_INGESTION=true
            shift
            ;;
        --skip-store)
            SKIP_STORE=true
            shift
            ;;
        --force-certs)
            FORCE_CERTS=true
            shift
            ;;
        --cleanup)
            CLEANUP_FIRST=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "🚀 End-to-End Data Pipeline Setup (Ingestion + Store Stages)"
            echo ""
            echo "Options:"
            echo "  --skip-ingestion  Skip ingestion stage setup (queue consumer)"
            echo "  --skip-store      Skip store stage setup (transformer service)"
            echo "  --force-certs     Force regenerate SSL certificates"
            echo "  --cleanup         Clean up existing environment before setup"
            echo "  --help            Show this help message"
            echo ""
            echo "Flow:"
            echo "  Kafka → Queue Consumer → Firehose (MVS) → S3 (wifi-scan)"
            echo "  → S3 Event → SQS → Transformer → Firehose (measurements) → S3"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Print functions
print_header() {
    echo ""
    echo -e "${MAGENTA}========================================${NC}"
    echo -e "${MAGENTA}$1${NC}"
    echo -e "${MAGENTA}========================================${NC}"
}

print_step() {
    echo -e "${GREEN}==>${NC} $1"
}

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Check if transformer service exists
check_transformer_service() {
    if [ ! -d "$TRANSFORMER_SERVICE_DIR" ]; then
        print_error "Transformer service directory not found: $TRANSFORMER_SERVICE_DIR"
        exit 1
    fi
    
    if [ ! -f "$TRANSFORMER_SERVICE_DIR/scripts/setup.sh" ]; then
        print_error "Transformer service setup script not found"
        exit 1
    fi
}

# Cleanup existing environment
cleanup_environment() {
    print_header "CLEANING UP EXISTING ENVIRONMENT"
    
    # Stop queue consumer Kafka
    print_step "Stopping queue consumer Kafka cluster..."
    cd "$SCRIPT_DIR"
    ./stop-local-kafka.sh 2>/dev/null || true
    
    # Stop LocalStack
    print_step "Stopping LocalStack..."
    docker stop localstack 2>/dev/null || true
    docker stop localstack-wifi-transformer 2>/dev/null || true
    docker rm localstack 2>/dev/null || true
    docker rm localstack-wifi-transformer 2>/dev/null || true
    
    # Clean up docker-compose LocalStack
    cd "$SCRIPT_DIR"
    docker-compose -f docker-compose-localstack.yml down 2>/dev/null || true
    
    # Prune Docker system
    print_step "Pruning Docker system..."
    docker system prune -f
    
    print_success "Environment cleaned up"
}

# Setup ingestion stage (queue consumer)
setup_ingestion_stage() {
    print_header "STAGE 1: INGESTION (Queue Consumer)"
    
    print_step "Setting up Kafka and queue consumer infrastructure..."
    cd "$SCRIPT_DIR"
    
    if [ "$FORCE_CERTS" = true ]; then
        ./setup.sh --force-certs
    else
        ./setup.sh
    fi
    
    if [ $? -ne 0 ]; then
        print_error "Ingestion stage setup failed"
        return 1
    fi
    
    print_success "Ingestion stage setup completed"
    return 0
}

# Configure transformer service to use correct bucket
configure_transformer_bucket() {
    print_step "Configuring transformer service to use ingestion output bucket..."
    
    # Update application-local.yml if needed
    local config_file="$TRANSFORMER_SERVICE_DIR/src/main/resources/application-local.yml"
    
    if [ -f "$config_file" ]; then
        # Check if bucket name needs updating
        if grep -q "ingested-wifiscan-data" "$config_file"; then
            print_info "Updating bucket name in transformer configuration..."
            # Create backup
            cp "$config_file" "$config_file.backup"
            # Replace bucket name
            sed -i.tmp "s/ingested-wifiscan-data/$INGESTION_S3_BUCKET/g" "$config_file"
            rm -f "$config_file.tmp"
            print_success "Transformer configuration updated to use bucket: $INGESTION_S3_BUCKET"
        else
            print_info "Transformer already configured with correct bucket"
        fi
    fi
}

# Setup store stage (transformer service)
setup_store_stage() {
    print_header "STAGE 2: STORE (Transformer Service)"
    
    # Configure transformer to use the correct bucket
    configure_transformer_bucket
    
    print_step "Setting up transformer service infrastructure..."
    
    # Create modified setup script for transformer to use existing LocalStack
    cat > /tmp/transformer-setup-modified.sh << 'EOSETUP'
#!/bin/bash
set -e

LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
SQS_QUEUE_NAME="wifi-scan-events"
S3_INGESTION_BUCKET="wifi-scan-data-bucket"  # Use queue consumer's output
S3_FIREHOSE_DESTINATION_BUCKET="wifi-measurements-table"
FIREHOSE_DELIVERY_STREAM_NAME="wifi-measurements-stream"

export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="$AWS_REGION"

echo "🔧 Setting up transformer service infrastructure..."

# Check LocalStack
if ! curl -s $LOCALSTACK_ENDPOINT/health > /dev/null; then
    echo "❌ LocalStack is not running. Please run ingestion stage first."
    exit 1
fi
echo "✅ LocalStack is running"

# Create SQS queue
echo "🔧 Creating SQS queue: $SQS_QUEUE_NAME"
aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
    --queue-name $SQS_QUEUE_NAME \
    --attributes '{
        "VisibilityTimeout": "300",
        "MessageRetentionPeriod": "1209600",
        "DelaySeconds": "0",
        "ReceiveMessageWaitTimeSeconds": "20"
    }' 2>/dev/null || echo "Queue may already exist"

aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs create-queue \
    --queue-name "${SQS_QUEUE_NAME}-dlq" \
    --attributes '{
        "VisibilityTimeout": "300",
        "MessageRetentionPeriod": "1209600"
    }' 2>/dev/null || echo "DLQ may already exist"

# Verify ingestion bucket exists
echo "🔍 Verifying ingestion bucket: $S3_INGESTION_BUCKET"
if ! aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_INGESTION_BUCKET > /dev/null 2>&1; then
    echo "❌ Ingestion bucket not found. Please run ingestion stage first."
    exit 1
fi
echo "✅ Ingestion bucket verified"

# Create destination bucket for measurements
echo "🔧 Creating S3 destination bucket..."
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://$S3_FIREHOSE_DESTINATION_BUCKET 2>/dev/null || echo "Firehose destination bucket may already exist"

# Enable versioning
aws --endpoint-url=$LOCALSTACK_ENDPOINT s3api put-bucket-versioning \
    --bucket $S3_FIREHOSE_DESTINATION_BUCKET \
    --versioning-configuration Status=Enabled 2>/dev/null || true

# Setup Firehose
echo "🔧 Setting up Kinesis Data Firehose..."
aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose create-delivery-stream \
    --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME \
    --delivery-stream-type "DirectPut" \
    --extended-s3-destination-configuration '{
        "RoleARN": "arn:aws:iam::000000000000:role/firehose-role",
        "BucketARN": "arn:aws:s3:::'$S3_FIREHOSE_DESTINATION_BUCKET'",
        "Prefix": "wifi-measurements/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/",
        "ErrorOutputPrefix": "errors/!{firehose:error-output-type}/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/",
        "BufferingHints": {
            "SizeInMBs": 1,
            "IntervalInSeconds": 5
        },
        "CompressionFormat": "GZIP",
        "EncryptionConfiguration": {
            "NoEncryptionConfig": "NoEncryption"
        }
    }' 2>/dev/null || echo "Firehose stream may already exist"

# Setup S3 event notification to SQS
echo "🔧 Setting up S3 event notifications..."
QUEUE_ARN="arn:aws:sqs:$AWS_REGION:000000000000:$SQS_QUEUE_NAME"

# Create notification configuration
cat > /tmp/s3-notification.json << EOF
{
    "QueueConfigurations": [
        {
            "Id": "wifi-scan-s3-event",
            "QueueArn": "$QUEUE_ARN",
            "Events": ["s3:ObjectCreated:*"],
            "Filter": {
                "Key": {
                    "FilterRules": [
                        {
                            "Name": "prefix",
                            "Value": "wifi-scan-data/"
                        }
                    ]
                }
            }
        }
    ]
}
EOF

aws --endpoint-url=$LOCALSTACK_ENDPOINT s3api put-bucket-notification-configuration \
    --bucket $S3_INGESTION_BUCKET \
    --notification-configuration file:///tmp/s3-notification.json 2>/dev/null || echo "S3 notification may already be configured"

rm -f /tmp/s3-notification.json

echo "✅ Transformer service infrastructure setup completed"
EOSETUP

    chmod +x /tmp/transformer-setup-modified.sh
    /tmp/transformer-setup-modified.sh
    
    if [ $? -ne 0 ]; then
        print_error "Store stage setup failed"
        return 1
    fi
    
    rm -f /tmp/transformer-setup-modified.sh
    print_success "Store stage setup completed"
    return 0
}

# Verify end-to-end setup
verify_end_to_end() {
    print_header "VERIFICATION: End-to-End Pipeline"
    
    export AWS_ACCESS_KEY_ID="test"
    export AWS_SECRET_ACCESS_KEY="test"
    export AWS_DEFAULT_REGION="$AWS_REGION"
    
    # Check Kafka
    print_step "Verifying Kafka cluster..."
    if ! docker ps | grep -q "kafka"; then
        print_warning "Kafka cluster not running"
        return 1
    fi
    print_success "Kafka cluster is running"
    
    # Check LocalStack
    print_step "Verifying LocalStack..."
    if ! curl -s $LOCALSTACK_ENDPOINT/health > /dev/null; then
        print_error "LocalStack is not responding"
        return 1
    fi
    print_success "LocalStack is healthy"
    
    # Check Firehose streams
    print_step "Verifying Firehose streams..."
    
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose describe-delivery-stream \
        --delivery-stream-name "MVS-stream" > /dev/null 2>&1; then
        print_success "Ingestion Firehose stream (MVS-stream) is active"
    else
        print_warning "Ingestion Firehose stream not found"
    fi
    
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose describe-delivery-stream \
        --delivery-stream-name "wifi-measurements-stream" > /dev/null 2>&1; then
        print_success "Store Firehose stream (wifi-measurements-stream) is active"
    else
        print_warning "Store Firehose stream not found"
    fi
    
    # Check S3 buckets
    print_step "Verifying S3 buckets..."
    
    local buckets=("wifi-scan-data-bucket" "wifi-measurements-table")
    for bucket in "${buckets[@]}"; do
        if aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$bucket > /dev/null 2>&1; then
            print_success "Bucket '$bucket' exists"
        else
            print_warning "Bucket '$bucket' not found"
        fi
    done
    
    # Check SQS queue
    print_step "Verifying SQS queue..."
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name "wifi-scan-events" > /dev/null 2>&1; then
        print_success "SQS queue 'wifi-scan-events' exists"
    else
        print_warning "SQS queue not found"
    fi
    
    print_success "End-to-end verification completed"
}

# Display summary and next steps
display_summary() {
    print_header "SETUP COMPLETED SUCCESSFULLY"
    
    echo ""
    echo -e "${CYAN}📋 Pipeline Configuration Summary:${NC}"
    echo ""
    echo -e "${GREEN}Stage 1: Ingestion (Queue Consumer)${NC}"
    echo "  ├─ Kafka Cluster: localhost:9092 (SSL: localhost:9093)"
    echo "  ├─ Firehose Stream: MVS-stream"
    echo "  └─ Output S3: s3://wifi-scan-data-bucket"
    echo ""
    echo -e "${GREEN}Stage 2: Store (Transformer Service)${NC}"
    echo "  ├─ Input S3: s3://wifi-scan-data-bucket"
    echo "  ├─ SQS Queue: wifi-scan-events"
    echo "  ├─ Firehose Stream: wifi-measurements-stream"
    echo "  └─ Output S3: s3://wifi-measurements-table"
    echo ""
    echo -e "${CYAN}💡 Note:${NC} Using 2 S3 buckets (LocalStack doesn't support Iceberg)"
    echo ""
    echo -e "${CYAN}🔗 Data Flow:${NC}"
    echo "  Kafka → Queue Consumer → Firehose (MVS) → S3 (wifi-scan)"
    echo "    ↓"
    echo "  S3 Event → SQS → Transformer → Firehose → S3 (measurements)"
    echo ""
    echo -e "${CYAN}🚀 Next Steps:${NC}"
    echo ""
    echo "1. Start Queue Consumer Service:"
    echo "   cd $PROJECT_ROOT"
    echo "   mvn spring-boot:run -Dspring-boot.run.profiles=local"
    echo ""
    echo "2. Start Transformer Service (in new terminal):"
    echo "   cd $TRANSFORMER_SERVICE_DIR"
    echo "   mvn spring-boot:run -Dspring-boot.run.profiles=local"
    echo ""
    echo "3. Send test message to Kafka:"
    echo "   cd $PARENT_SCRIPTS_DIR"
    echo "   ./test/send-test-message.sh"
    echo ""
    echo "4. Monitor the pipeline:"
    echo "   - Check queue consumer logs for Kafka consumption"
    echo "   - Check S3 for ingested files:"
    echo "     aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://wifi-scan-data-bucket/ --recursive"
    echo "   - Check SQS for S3 events:"
    echo "     aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs receive-message --queue-url $LOCALSTACK_ENDPOINT/000000000000/wifi-scan-events"
    echo "   - Check transformer logs for processing"
    echo "   - Check final output:"
    echo "     aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://wifi-measurements-table/ --recursive"
    echo ""
    echo -e "${CYAN}🛠️  Useful Commands:${NC}"
    echo ""
    echo "Check Kafka topics:"
    echo "  cd $SCRIPT_DIR && ./list-topics.sh"
    echo ""
    echo "View LocalStack health:"
    echo "  curl $LOCALSTACK_ENDPOINT/health"
    echo ""
    echo "List Firehose streams:"
    echo "  aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose list-delivery-streams"
    echo ""
    echo "Stop environment:"
    echo "  cd $SCRIPT_DIR && ./stop-local-kafka.sh"
    echo "  docker stop localstack"
    echo ""
    echo -e "${YELLOW}⚠️  Important Notes:${NC}"
    echo "  - Both services must be running for end-to-end flow"
    echo "  - S3 event notifications may take a few seconds to trigger"
    echo "  - Firehose buffering (5 seconds) may delay S3 writes"
    echo ""
}

# Main execution
main() {
    print_header "END-TO-END DATA PIPELINE SETUP"
    print_info "Setting up Ingestion and Store stages"
    print_info "Project root: $PROJECT_ROOT"
    print_info "Transformer service: $TRANSFORMER_SERVICE_DIR"
    
    # Check prerequisites
    check_transformer_service
    
    # Cleanup if requested
    if [ "$CLEANUP_FIRST" = true ]; then
        cleanup_environment
    fi
    
    # Setup stages
    if [ "$SKIP_INGESTION" = false ]; then
        if ! setup_ingestion_stage; then
            print_error "Failed to setup ingestion stage"
            exit 1
        fi
    else
        print_info "Skipping ingestion stage setup"
    fi
    
    # Add brief pause between stages
    print_step "Pausing for services to stabilize..."
    sleep 3
    
    if [ "$SKIP_STORE" = false ]; then
        if ! setup_store_stage; then
            print_error "Failed to setup store stage"
            exit 1
        fi
    else
        print_info "Skipping store stage setup"
    fi
    
    # Verify end-to-end setup
    verify_end_to_end
    
    # Display summary
    display_summary
}

# Run main function
main

