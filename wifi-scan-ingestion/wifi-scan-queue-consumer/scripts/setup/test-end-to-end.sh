#!/bin/bash

# test-end-to-end.sh
# End-to-end test script for the complete data pipeline

set -e

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT_SCRIPTS_DIR="$(dirname "$SCRIPT_DIR")"

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

# Test data
create_test_data() {
    cat << 'EOF'
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
        "ssid": "TestNetwork-E2E",
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
          "ssid": "TestNetwork-E2E",
          "bssid": "b8:f8:53:c0:1e:ff",
          "scantime": 1731091613712,
          "rssi": -61
        }
      ]
    }
  ]
}
EOF
}

# Check prerequisites
check_prerequisites() {
    print_header "CHECKING PREREQUISITES"
    
    # Check if services are running
    print_step "Checking Docker containers..."
    if ! docker ps | grep -q "kafka"; then
        print_error "Kafka cluster not running"
        print_info "Start the pipeline first: ./setup-ingestion-store-stage.sh"
        exit 1
    fi
    print_success "Kafka cluster is running"
    
    if ! docker ps | grep -qi "localstack"; then
        print_error "LocalStack not running"
        exit 1
    fi
    print_success "LocalStack is running"
    
    # Check LocalStack health
    print_step "Checking LocalStack health..."
    if ! curl -s $LOCALSTACK_ENDPOINT/health > /dev/null; then
        print_error "LocalStack is not responding"
        exit 1
    fi
    print_success "LocalStack is healthy"
    
    # Check if Java services are running
    print_step "Checking Java services..."
    print_warning "Ensure both services are running:"
    print_info "  1. Queue Consumer Service"
    print_info "  2. Transformer Service"
    echo ""
    read -p "Are both services running? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_warning "Please start both services first"
        echo ""
        echo "Terminal 1 - Queue Consumer:"
        echo "  cd wifi-scan-queue-consumer"
        echo "  mvn spring-boot:run -Dspring-boot.run.profiles=local"
        echo ""
        echo "Terminal 2 - Transformer:"
        echo "  cd wifi-measurements-transformer-service"
        echo "  mvn spring-boot:run -Dspring-boot.run.profiles=local"
        echo ""
        exit 1
    fi
    
    print_success "Prerequisites satisfied"
}

# Get baseline counts
get_baseline() {
    print_header "ESTABLISHING BASELINE"
    
    BASELINE_INGESTION_FILES=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_INGESTION_BUCKET --recursive 2>/dev/null | wc -l || echo "0")
    BASELINE_OUTPUT_FILES=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_OUTPUT_BUCKET --recursive 2>/dev/null | wc -l || echo "0")
    
    print_info "Baseline ingestion files: $BASELINE_INGESTION_FILES"
    print_info "Baseline output files: $BASELINE_OUTPUT_FILES"
}

# Send test message to Kafka
send_test_message() {
    print_header "STEP 1: SENDING TEST MESSAGE TO KAFKA"
    
    local test_data=$(create_test_data)
    local test_file="/tmp/e2e-test-$(date +%s).json"
    
    echo "$test_data" > "$test_file"
    
    print_step "Sending WiFi scan message to Kafka topic 'wifi-scan-data'..."
    
    cd "$PARENT_SCRIPTS_DIR"
    if ./test/send-test-message.sh "$test_data"; then
        print_success "Message sent to Kafka successfully"
    else
        print_error "Failed to send message to Kafka"
        rm -f "$test_file"
        exit 1
    fi
    
    rm -f "$test_file"
}

# Wait and check S3 ingestion bucket
check_ingestion_bucket() {
    print_header "STEP 2: CHECKING S3 INGESTION BUCKET"
    
    print_step "Waiting for message to be processed by Queue Consumer and written to S3..."
    print_info "This may take 60-90 seconds due to Firehose buffering..."
    
    local max_wait=120
    local elapsed=0
    local check_interval=5
    
    while [ $elapsed -lt $max_wait ]; do
        local current_files=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_INGESTION_BUCKET --recursive 2>/dev/null | wc -l || echo "0")
        
        if [ "$current_files" -gt "$BASELINE_INGESTION_FILES" ]; then
            print_success "New file detected in ingestion bucket!"
            echo ""
            print_info "Latest files:"
            aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_INGESTION_BUCKET --recursive 2>/dev/null | tail -5
            return 0
        fi
        
        printf "\r  Waiting... %ds elapsed (checking every %ds)" "$elapsed" "$check_interval"
        sleep $check_interval
        elapsed=$((elapsed + check_interval))
    done
    
    echo ""
    print_error "Timeout waiting for file in ingestion bucket"
    print_warning "Check queue consumer logs for errors"
    return 1
}

# Check SQS queue for S3 events
check_sqs_events() {
    print_header "STEP 3: CHECKING SQS QUEUE FOR S3 EVENTS"
    
    print_step "Checking for S3 event notifications in SQS..."
    
    local queue_url="$LOCALSTACK_ENDPOINT/000000000000/$SQS_QUEUE_NAME"
    local max_wait=30
    local elapsed=0
    
    while [ $elapsed -lt $max_wait ]; do
        local message_count=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs get-queue-attributes \
            --queue-url "$queue_url" \
            --attribute-names ApproximateNumberOfMessages \
            --query 'Attributes.ApproximateNumberOfMessages' --output text 2>/dev/null || echo "0")
        
        if [ "$message_count" -gt 0 ]; then
            print_success "S3 event detected in SQS queue!"
            print_info "Messages in queue: $message_count"
            
            # Peek at a message
            print_info "Sample message (preview):"
            aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs receive-message \
                --queue-url "$queue_url" \
                --max-number-of-messages 1 \
                --visibility-timeout 0 \
                --query 'Messages[0].Body' --output text 2>/dev/null | jq -C '.' | head -20 || true
            
            return 0
        fi
        
        printf "\r  Waiting for S3 event... %ds elapsed" "$elapsed"
        sleep 2
        elapsed=$((elapsed + 2))
    done
    
    echo ""
    print_warning "No S3 events detected in queue (may already be processed)"
    return 0  # Not fatal, transformer may have already processed
}

# Check output bucket for processed data
check_output_bucket() {
    print_header "STEP 4: CHECKING OUTPUT BUCKET FOR PROCESSED DATA"
    
    print_step "Waiting for Transformer Service to process and write to output bucket..."
    print_info "This may take 60-90 seconds due to Firehose buffering..."
    
    local max_wait=120
    local elapsed=0
    local check_interval=5
    
    while [ $elapsed -lt $max_wait ]; do
        local current_files=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_OUTPUT_BUCKET --recursive 2>/dev/null | wc -l || echo "0")
        
        if [ "$current_files" -gt "$BASELINE_OUTPUT_FILES" ]; then
            print_success "New file detected in output bucket!"
            echo ""
            print_info "Latest files:"
            aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_OUTPUT_BUCKET --recursive 2>/dev/null | tail -5
            
            # Download and display sample content
            print_info "Sample content from latest file:"
            local latest_file=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_OUTPUT_BUCKET --recursive 2>/dev/null | tail -1 | awk '{print $4}')
            if [ -n "$latest_file" ]; then
                aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 cp \
                    "s3://$S3_OUTPUT_BUCKET/$latest_file" /tmp/e2e-output.gz 2>/dev/null || true
                
                if [ -f /tmp/e2e-output.gz ]; then
                    print_info "Decompressing and displaying content:"
                    zcat /tmp/e2e-output.gz | head -100 | jq -C '.' 2>/dev/null || zcat /tmp/e2e-output.gz | head -100
                    rm -f /tmp/e2e-output.gz
                fi
            fi
            
            return 0
        fi
        
        printf "\r  Waiting... %ds elapsed (checking every %ds)" "$elapsed" "$check_interval"
        sleep $check_interval
        elapsed=$((elapsed + check_interval))
    done
    
    echo ""
    print_error "Timeout waiting for file in output bucket"
    print_warning "Check transformer service logs for errors"
    return 1
}

# Display test summary
display_summary() {
    print_header "END-TO-END TEST SUMMARY"
    
    local final_ingestion_files=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_INGESTION_BUCKET --recursive 2>/dev/null | wc -l || echo "0")
    local final_output_files=$(aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 ls s3://$S3_OUTPUT_BUCKET --recursive 2>/dev/null | wc -l || echo "0")
    
    local new_ingestion_files=$((final_ingestion_files - BASELINE_INGESTION_FILES))
    local new_output_files=$((final_output_files - BASELINE_OUTPUT_FILES))
    
    echo ""
    echo -e "${CYAN}Test Results:${NC}"
    echo "  📥 New ingestion files: $new_ingestion_files"
    echo "  📤 New output files: $new_output_files"
    echo ""
    
    if [ "$new_ingestion_files" -gt 0 ] && [ "$new_output_files" -gt 0 ]; then
        print_success "END-TO-END TEST PASSED! ✓"
        echo ""
        echo -e "${GREEN}Data successfully flowed through entire pipeline:${NC}"
        echo "  Kafka → Queue Consumer → Firehose → S3 (ingestion)"
        echo "    → S3 Event → SQS → Transformer → Firehose → S3 (output)"
        return 0
    else
        print_error "END-TO-END TEST FAILED!"
        echo ""
        echo "Please check:"
        echo "  • Queue Consumer logs"
        echo "  • Transformer Service logs"
        echo "  • LocalStack logs: docker logs localstack"
        return 1
    fi
}

# Main execution
main() {
    echo -e "${MAGENTA}╔════════════════════════════════════════╗${NC}"
    echo -e "${MAGENTA}║     END-TO-END PIPELINE TEST           ║${NC}"
    echo -e "${MAGENTA}╚════════════════════════════════════════╝${NC}"
    echo ""
    
    check_prerequisites
    get_baseline
    
    if send_test_message; then
        if check_ingestion_bucket; then
            check_sqs_events
            if check_output_bucket; then
                display_summary
                exit 0
            fi
        fi
    fi
    
    display_summary
    exit 1
}

# Run main function
main

