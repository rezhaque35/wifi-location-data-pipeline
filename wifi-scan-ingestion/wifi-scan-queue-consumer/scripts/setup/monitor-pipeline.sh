#!/bin/bash

# monitor-pipeline.sh
# Monitor the end-to-end data pipeline flow

set -e

# Configuration
LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
S3_INGESTION_BUCKET="wifi-scan-data-bucket"
S3_OUTPUT_BUCKET="wifi-measurements-table"
SQS_QUEUE_NAME="wifi-scan-events"

export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="$AWS_REGION"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

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

print_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

# Check Docker containers
check_containers() {
    print_header "DOCKER CONTAINERS"
    
    echo -e "${CYAN}Kafka & Zookeeper:${NC}"
    if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "(kafka|zookeeper)"; then
        print_success "Kafka cluster is running"
    else
        print_warning "Kafka cluster not found"
    fi
    
    echo ""
    echo -e "${CYAN}LocalStack:${NC}"
    if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -i "localstack"; then
        print_success "LocalStack is running"
    else
        print_error "LocalStack not found"
    fi
}

# Check LocalStack health
check_localstack_health() {
    print_header "LOCALSTACK HEALTH"
    
    if curl -s $LOCALSTACK_ENDPOINT/health > /dev/null 2>&1; then
        local health_status=$(curl -s $LOCALSTACK_ENDPOINT/health | jq -r '.services')
        echo -e "${CYAN}Services Status:${NC}"
        echo "$health_status" | jq -C '.'
        print_success "LocalStack is healthy"
    else
        print_error "LocalStack is not responding"
    fi
}

# Check Kafka topics
check_kafka_topics() {
    print_header "KAFKA TOPICS"
    
    if docker ps | grep -q "kafka"; then
        local topics=$(docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null)
        if [ $? -eq 0 ]; then
            echo -e "${CYAN}Available topics:${NC}"
            echo "$topics"
            print_success "Kafka topics listed"
        else
            print_warning "Could not list Kafka topics"
        fi
    else
        print_error "Kafka container not running"
    fi
}

# Check S3 buckets and contents
check_s3_buckets() {
    print_header "S3 BUCKETS"
    
    # Ingestion bucket
    echo -e "${CYAN}Ingestion Bucket: $S3_INGESTION_BUCKET${NC}"
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_INGESTION_BUCKET --recursive 2>/dev/null | tail -20; then
        local file_count=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_INGESTION_BUCKET --recursive 2>/dev/null | wc -l)
        print_success "Bucket exists with $file_count files (showing last 20)"
    else
        print_warning "Bucket is empty or doesn't exist"
    fi
    
    echo ""
    
    # Output bucket
    echo -e "${CYAN}Output Bucket: $S3_OUTPUT_BUCKET${NC}"
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_OUTPUT_BUCKET --recursive 2>/dev/null | tail -20; then
        local file_count=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_OUTPUT_BUCKET --recursive 2>/dev/null | wc -l)
        print_success "Bucket exists with $file_count files (showing last 20)"
    else
        print_warning "Bucket is empty or doesn't exist"
    fi
}

# Check SQS queue
check_sqs_queue() {
    print_header "SQS QUEUE"
    
    echo -e "${CYAN}Queue: $SQS_QUEUE_NAME${NC}"
    
    if aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name $SQS_QUEUE_NAME > /dev/null 2>&1; then
        local queue_url=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-url --queue-name $SQS_QUEUE_NAME --query 'QueueUrl' --output text)
        
        # Get queue attributes
        local attributes=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-attributes \
            --queue-url "$queue_url" \
            --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible ApproximateNumberOfMessagesDelayed \
            --query 'Attributes' --output json)
        
        echo -e "${CYAN}Queue Attributes:${NC}"
        echo "$attributes" | jq -C '.'
        
        # Peek at messages (without removing them)
        local message_count=$(echo "$attributes" | jq -r '.ApproximateNumberOfMessages')
        if [ "$message_count" -gt 0 ]; then
            print_info "Peeking at messages (not removing)..."
            aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs receive-message \
                --queue-url "$queue_url" \
                --max-number-of-messages 1 \
                --visibility-timeout 0 \
                --query 'Messages[0]' --output json 2>/dev/null | jq -C '.' || true
        fi
        
        print_success "Queue exists with $message_count messages"
    else
        print_error "Queue not found"
    fi
}

# Check Firehose streams
check_firehose_streams() {
    print_header "FIREHOSE DELIVERY STREAMS"
    
    local streams=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose list-delivery-streams --query 'DeliveryStreamNames' --output json 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        echo -e "${CYAN}Available Streams:${NC}"
        echo "$streams" | jq -C '.[]'
        
        # Check each stream status
        for stream in $(echo "$streams" | jq -r '.[]'); do
            echo ""
            echo -e "${CYAN}Stream: $stream${NC}"
            local status=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose describe-delivery-stream \
                --delivery-stream-name "$stream" \
                --query 'DeliveryStreamDescription.DeliveryStreamStatus' \
                --output text 2>/dev/null)
            
            if [ "$status" = "ACTIVE" ]; then
                print_success "Status: $status"
            else
                print_warning "Status: $status"
            fi
        done
    else
        print_error "Could not list Firehose streams"
    fi
}

# Check Java processes (services)
check_java_services() {
    print_header "JAVA SERVICES"
    
    echo -e "${CYAN}Running Java processes:${NC}"
    local java_procs=$(ps aux | grep -i "[j]ava.*spring-boot" | grep -v grep)
    
    if [ -n "$java_procs" ]; then
        echo "$java_procs" | awk '{print "PID: "$2" - "$11" "$12" "$13}'
        print_success "Java services are running"
    else
        print_warning "No Java Spring Boot services found running"
        print_info "Start queue consumer: cd wifi-scan-queue-consumer && mvn spring-boot:run -Dspring-boot.run.profiles=local"
        print_info "Start transformer: cd wifi-measurements-transformer-service && mvn spring-boot:run -Dspring-boot.run.profiles=local"
    fi
}

# Display pipeline metrics
show_metrics() {
    print_header "PIPELINE METRICS"
    
    # Count files in each stage
    local ingestion_files=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_INGESTION_BUCKET --recursive 2>/dev/null | wc -l || echo "0")
    local output_files=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_OUTPUT_BUCKET --recursive 2>/dev/null | wc -l || echo "0")
    local queue_messages=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-attributes \
        --queue-url "$LOCALSTACK_ENDPOINT/000000000000/$SQS_QUEUE_NAME" \
        --attribute-names ApproximateNumberOfMessages \
        --query 'Attributes.ApproximateNumberOfMessages' --output text 2>/dev/null || echo "0")
    
    echo ""
    echo -e "${CYAN}Pipeline Statistics:${NC}"
    echo "  📥 Ingested Files (S3): $ingestion_files"
    echo "  📨 Pending Events (SQS): $queue_messages"
    echo "  📤 Processed Files (S3): $output_files"
    echo ""
    
    # Calculate conversion rate
    if [ "$ingestion_files" -gt 0 ]; then
        local conversion_rate=$(echo "scale=2; $output_files * 100 / $ingestion_files" | bc)
        echo -e "  ${GREEN}Conversion Rate: ${conversion_rate}%${NC}"
    fi
}

# Show usage
show_usage() {
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Monitor the end-to-end data pipeline"
    echo ""
    echo "Options:"
    echo "  --watch, -w      Continuous monitoring (refresh every 5 seconds)"
    echo "  --help, -h       Show this help message"
    echo ""
    echo "Components monitored:"
    echo "  • Docker containers (Kafka, LocalStack)"
    echo "  • LocalStack health"
    echo "  • Kafka topics"
    echo "  • S3 buckets and contents"
    echo "  • SQS queue status"
    echo "  • Firehose delivery streams"
    echo "  • Running Java services"
    echo "  • Pipeline metrics"
}

# Main monitoring function
monitor_once() {
    clear
    echo -e "${MAGENTA}╔════════════════════════════════════════╗${NC}"
    echo -e "${MAGENTA}║  DATA PIPELINE MONITORING DASHBOARD   ║${NC}"
    echo -e "${MAGENTA}╚════════════════════════════════════════╝${NC}"
    echo -e "${CYAN}Timestamp: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
    
    check_containers
    check_localstack_health
    check_kafka_topics
    check_firehose_streams
    check_s3_buckets
    check_sqs_queue
    check_java_services
    show_metrics
    
    echo ""
    print_info "Press Ctrl+C to exit"
}

# Main execution
main() {
    case "${1:-}" in
        --watch|-w)
            while true; do
                monitor_once
                sleep 5
            done
            ;;
        --help|-h)
            show_usage
            ;;
        "")
            monitor_once
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
}

# Run main function
main "$@"

